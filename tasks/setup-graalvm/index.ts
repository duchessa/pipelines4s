import toolLib = require('azure-pipelines-tool-lib/tool');
import taskLib = require('azure-pipelines-task-lib/task');
import fs = require('fs');
import path = require('path');
import os = require('os');

const enum Arch {
    X64 = "x64",
    Arm64 = "arm64"
}

function resolveArch(arch: string): Arch {
    if (arch === Arch.X64) return Arch.X64
    else if (arch === Arch.Arm64) return Arch.Arm64
    else throw `GraalVM CE only supports "x64" (AMD64) or "arm64" (AArch64) runtime architectures at this time.`
}

function resolveHome(root: string, platform: taskLib.Platform): string {
    return platform === taskLib.Platform.MacOS ? path.join(root, "Home") : root
}

class GraalVersion {
    readonly major: number
    readonly minor: number
    readonly patch: number
    readonly update?: number

    short(): string {
        return `${this.major}.${this.minor}.${this.patch}`
    }

    full(): string {
        if (typeof this.update === "undefined") return this.short()
        else return `${this.short()}.${this.update}`
    }

    public toString: () => string = this.full

    constructor(version: string) {
        const graalVersionPattern = /^(?<major>[1-9]\d*)\.(?<minor>0|[1-9]\d*)\.(?<patch>0|[1-9]\d*)(?:\.(?<update>0|[1-9]\d*))?$/
        const res = graalVersionPattern.exec(version)
        if (res === null) throw `Unable to initialise GraalVersion instance from provided setting: ${version}. Version must match the following pattern: ${graalVersionPattern}`
        else {
            const groups = res.groups!;
            const int = (str: string) => parseInt(str, 10);
            this.major = int(groups.major);
            this.minor = int(groups.minor);
            this.patch = int(groups.patch);
            this.update = typeof groups.update === "undefined" ? undefined : int(groups.update);
        }
    }

}

function resolveInstalledVersion(graalHome: string): GraalVersion {
    const releaseFile = path.join(graalHome, "release")
    taskLib.debug(`Verifying installed GraalVM version using 'release' file at ${releaseFile}.`)
    try {
        const extractor = /^GRAALVM_VERSION\s*=\s*"((?:[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:\.(?:0|[1-9]\d*))?)"/mi
        const parsed = fs.readFileSync(releaseFile).toString().match(extractor)
        const installed = new GraalVersion(parsed![1])
        taskLib.debug(`Resolved installed release version ${installed.full()} at ${graalHome}.`)
        return installed
    } catch (err) {
        throw `Error resolving installed GraalVM version. Unable to read version information from 'release' file at ${releaseFile}. ${err}`
    }
}

async function downloadGraalDistribution(platform: taskLib.Platform, arch: Arch, graalVersion: GraalVersion, javaVersion: number) {

    function platformUrlComponent(): string {
        switch (platform) {
            case taskLib.Platform.Linux:
                return "linux"
            case taskLib.Platform.MacOS:
                return "darwin"
            case taskLib.Platform.Windows:
                return "windows"
            default:
                throw "Unsupported Platform. GraalVM CE supports only Linux, Darwin, or Windows hosts at this time."
        }
    }

    function archUrlComponent() {
        if (arch === Arch.Arm64 && platform !== taskLib.Platform.Linux) throw `GraalVM CE only supports "arm64" (AArch64) runtime architectures when running on linux hosts at this time.`;
        return arch === Arch.X64 ? "amd64" : "aarch64";
    }

    function extensionUrlComponent() {
        return platform === taskLib.Platform.Windows ? "zip" : "tar.gz";
    }

    const downloadUrl = `https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-${graalVersion.full()}/graalvm-ce-java${javaVersion}-${platformUrlComponent()}-${archUrlComponent()}-${graalVersion.full()}.${extensionUrlComponent()}`

    taskLib.debug(`Downloading GraalVM distribution for ${taskLib.Platform[platform]}-${arch} from: ${downloadUrl}`);
    const downloaded = await toolLib.downloadTool(downloadUrl);
    taskLib.debug(`Downloaded GraalVM distribution to: ${downloaded}`);

    const extracted: string = platform === taskLib.Platform.Windows ? await toolLib.extractZip(downloaded) : await toolLib.extractTar(downloaded);

    taskLib.debug(`Extracted GraalVM distribution to: ${extracted}`);

    const toolPath: string =
        platform === taskLib.Platform.MacOS ?
            path.join(extracted, `graalvm-ce-java${javaVersion}-${graalVersion.full()}`, "Contents") :
            path.join(extracted, `graalvm-ce-java${javaVersion}-${graalVersion.full()}`);

    const cached = await toolLib.cacheDir(toolPath, `graalvm-ce-java${javaVersion}`, graalVersion.short());

    taskLib.debug(`Cached GraalVM ${graalVersion.full()} distribution locally at ${cached}. Contents: ${fs.readdirSync(cached).join(", ")}`);
}


async function configureLocalTool(localPath: string,
                                  platform: taskLib.Platform,
                                  nativeImage: boolean,
                                  llvmToolchain: boolean,
                                  prefixNodeExecutables: boolean) {

    async function configureNodeExecutablesPrefix(graalBinDir: string): Promise<void> {
        try {
            const nodeExecutablesBase = ["node", "npm", "npx"]
            const nodeExecutables =
                nodeExecutablesBase
                    .concat(nodeExecutablesBase.map(f => `${f}.cmd`))
                    .concat(nodeExecutablesBase.map(f => `${f}.bat`))
                    .concat(nodeExecutablesBase.map(f => `${f}.exe`))

            const prefix = "graal-"

            const resolvedNodeBinaries = prefixNodeExecutables ?
                fs.readdirSync(graalBinDir).filter(file => nodeExecutables.includes(file)) :
                fs.readdirSync(graalBinDir).filter(file => nodeExecutables.map(f => `${prefix}${f}`).includes(file))
            taskLib.debug(`Resolved GraalVM Node binaries: ${resolvedNodeBinaries.join(", ")}`)

            if (prefixNodeExecutables) {
                resolvedNodeBinaries.forEach(f => {
                    const oldPath = path.join(graalBinDir, f)
                    const newPath = path.join(graalBinDir, `${prefix}${f}`)
                    taskLib.debug(`Renaming ${oldPath} to ${newPath}.`)
                    fs.renameSync(oldPath, newPath)
                });
            } else {
                resolvedNodeBinaries.forEach(f => {
                        const oldPath = path.join(graalBinDir, f)
                        const newPath = path.join(graalBinDir, f.substring(prefix.length))
                        taskLib.debug(`Renaming ${oldPath} to ${newPath}.`)
                        fs.renameSync(oldPath, newPath)
                    }
                );
            }
        } catch (err) {
            throw `Error setting up GraalVM NodeJS executables prefix: Configured setting: prefixNodeExecutables := '${prefixNodeExecutables}'`
        }
    }


    async function installGraalComponents(graalBinDir: string, components: string[]): Promise<void> {
        const gu = path.join(graalBinDir, "gu")
        components.forEach(component => {
            const args = ` -A -N install -n ${component}`
            taskLib.debug(`Installing GraalVM "${component}" component using ${gu}.`)
            const result = taskLib.execSync(gu, args)
            if (result.code != 0) throw `Error installing GraalVM "${component}" component. ${result.stderr}`
        })
    }

    function components(): string[] {
        const pairs: [boolean, string][] = [
            [nativeImage, "native-image"],
            [llvmToolchain, "llvm-toolchain"]
        ];
        return pairs.filter((pair) => pair[0]).map((pair) => pair[1]);
    }


    taskLib.debug(`Using GraalVM from ${localPath}`);

    const graalHome = resolveHome(localPath, platform);
    taskLib.debug(`Setting GRAALVM_HOME and JAVA_HOME as ${graalHome}. Contents: ${fs.readdirSync(graalHome).join(", ")}`);
    taskLib.setVariable("GRAALVM_HOME", graalHome);
    taskLib.setVariable("JAVA_HOME", graalHome);

    const binDir = path.join(graalHome, "bin");
    taskLib.debug(`Using GraalVM bin directory at ${binDir}. Contents: ${fs.readdirSync(binDir).join(", ")}`);
    taskLib.prependPath(binDir);

    await installGraalComponents(binDir, components());

    await configureNodeExecutablesPrefix(binDir);
}

async function run(platform: taskLib.Platform,
                   arch: Arch,
                   graalVersion: GraalVersion,
                   javaVersion: number,
                   nativeImage: boolean,
                   llvmToolchain: boolean,
                   prefixNodeExecutables: boolean) {

    try {
        if (toolLib.findLocalToolVersions(`graalvm-ce-java${javaVersion}`).includes(graalVersion.short())) {
            const resolvedPath = toolLib.findLocalTool(`graalvm-ce-java${javaVersion}`, graalVersion.short());
            const installedVersion = resolveInstalledVersion(resolveHome(resolvedPath, platform));
            if (installedVersion.full() === graalVersion.full()) {
                taskLib.debug(`Using locally cached GraalVM ${graalVersion.full()} for Java ${javaVersion} from ${resolvedPath}.`);
                await configureLocalTool(resolvedPath, platform, nativeImage, llvmToolchain, prefixNodeExecutables)
            } else {
                taskLib.debug(`Locally cached GraalVM ${installedVersion.full()} does not equal required ${graalVersion.full()}.`)
                await downloadGraalDistribution(platform, arch, graalVersion, javaVersion)
                await run(platform, arch, graalVersion, javaVersion, nativeImage, llvmToolchain, prefixNodeExecutables);
            }


        } else {
            await downloadGraalDistribution(platform, arch, graalVersion, javaVersion)
            await run(platform, arch, graalVersion, javaVersion, nativeImage, llvmToolchain, prefixNodeExecutables);
        }

    } catch (err) {
        taskLib.setResult(taskLib.TaskResult.Failed, `Error installing GraalVM ${graalVersion} for Java ${javaVersion} on ${arch}. ${err}`);
    }

}

run(taskLib.getPlatform(),
    resolveArch(os.arch()),
    new GraalVersion(taskLib.getInput("graalVersion", true)!),
    parseInt(taskLib.getInput("javaVersion", true)!, 10),
    taskLib.getBoolInput("nativeImage", true)!,
    taskLib.getBoolInput("llvmToolchain", true)!,
    taskLib.getBoolInput("prefixNodeExecutables", true)!);
