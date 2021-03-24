"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
const toolLib = require("azure-pipelines-tool-lib/tool");
const taskLib = require("azure-pipelines-task-lib/task");
const fs = require("fs");
const path = require("path");
const os = require("os");
function resolveArch(arch) {
    if (arch === "x64" /* X64 */)
        return "x64" /* X64 */;
    else if (arch === "arm64" /* Arm64 */)
        return "arm64" /* Arm64 */;
    else
        throw `GraalVM CE only supports "x64" (AMD64) or "arm64" (AArch64) runtime architectures at this time.`;
}
function resolveHome(root, platform) {
    return platform === taskLib.Platform.MacOS ? path.join(root, "Home") : root;
}
class GraalVersion {
    constructor(version) {
        this.toString = this.full;
        const graalVersionPattern = /^(?<major>[1-9]\d*)\.(?<minor>0|[1-9]\d*)\.(?<patch>0|[1-9]\d*)(?:\.(?<update>0|[1-9]\d*))?$/;
        const res = graalVersionPattern.exec(version);
        if (res === null)
            throw `Unable to initialise GraalVersion instance from provided setting: ${version}. Version must match the following pattern: ${graalVersionPattern}`;
        else {
            const groups = res.groups;
            const int = (str) => parseInt(str, 10);
            this.major = int(groups.major);
            this.minor = int(groups.minor);
            this.patch = int(groups.patch);
            this.update = typeof groups.update === "undefined" ? undefined : int(groups.update);
        }
    }
    short() {
        return `${this.major}.${this.minor}.${this.patch}`;
    }
    full() {
        if (typeof this.update === "undefined")
            return this.short();
        else
            return `${this.short()}.${this.update}`;
    }
}
function resolveInstalledVersion(graalHome) {
    const releaseFile = path.join(graalHome, "release");
    taskLib.debug(`Verifying installed GraalVM version using 'release' file at ${releaseFile}.`);
    try {
        const extractor = /^GRAALVM_VERSION\s*=\s*"((?:[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:\.(?:0|[1-9]\d*))?)"/mi;
        const parsed = fs.readFileSync(releaseFile).toString().match(extractor);
        const installed = new GraalVersion(parsed[1]);
        taskLib.debug(`Resolved installed release version ${installed.full()} at ${graalHome}.`);
        return installed;
    }
    catch (err) {
        throw `Error resolving installed GraalVM version. Unable to read version information from 'release' file at ${releaseFile}. ${err}`;
    }
}
function downloadGraalDistribution(platform, arch, graalVersion, javaVersion) {
    return __awaiter(this, void 0, void 0, function* () {
        function platformUrlComponent() {
            switch (platform) {
                case taskLib.Platform.Linux:
                    return "linux";
                case taskLib.Platform.MacOS:
                    return "darwin";
                case taskLib.Platform.Windows:
                    return "windows";
                default:
                    throw "Unsupported Platform. GraalVM CE supports only Linux, Darwin, or Windows hosts at this time.";
            }
        }
        function archUrlComponent() {
            if (arch === "arm64" /* Arm64 */ && platform !== taskLib.Platform.Linux)
                throw `GraalVM CE only supports "arm64" (AArch64) runtime architectures when running on linux hosts at this time.`;
            return arch === "x64" /* X64 */ ? "amd64" : "aarch64";
        }
        function extensionUrlComponent() {
            return platform === taskLib.Platform.Windows ? "zip" : "tar.gz";
        }
        const downloadUrl = `https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-${graalVersion.full()}/graalvm-ce-java${javaVersion}-${platformUrlComponent()}-${archUrlComponent()}-${graalVersion.full()}.${extensionUrlComponent()}`;
        taskLib.debug(`Downloading GraalVM distribution for ${taskLib.Platform[platform]}-${arch} from: ${downloadUrl}`);
        const downloaded = yield toolLib.downloadTool(downloadUrl);
        taskLib.debug(`Downloaded GraalVM distribution to: ${downloaded}`);
        const extracted = platform === taskLib.Platform.Windows ? yield toolLib.extractZip(downloaded) : yield toolLib.extractTar(downloaded);
        taskLib.debug(`Extracted GraalVM distribution to: ${extracted}`);
        const toolPath = platform === taskLib.Platform.MacOS ?
            path.join(extracted, `graalvm-ce-java${javaVersion}-${graalVersion.full()}`, "Contents") :
            path.join(extracted, `graalvm-ce-java${javaVersion}-${graalVersion.full()}`);
        const cached = yield toolLib.cacheDir(toolPath, `graalvm-ce-java${javaVersion}`, graalVersion.short());
        taskLib.debug(`Cached GraalVM ${graalVersion.full()} distribution locally at ${cached}. Contents: ${fs.readdirSync(cached).join(", ")}`);
    });
}
function configureLocalTool(localPath, platform, nativeImage, llvmToolchain, prefixNodeExecutables) {
    return __awaiter(this, void 0, void 0, function* () {
        function configureNodeExecutablesPrefix(graalBinDir) {
            return __awaiter(this, void 0, void 0, function* () {
                try {
                    const nodeExecutablesBase = ["node", "npm", "npx"];
                    const nodeExecutables = nodeExecutablesBase
                        .concat(nodeExecutablesBase.map(f => `${f}.cmd`))
                        .concat(nodeExecutablesBase.map(f => `${f}.bat`))
                        .concat(nodeExecutablesBase.map(f => `${f}.exe`));
                    const prefix = "graal-";
                    const resolvedNodeBinaries = prefixNodeExecutables ?
                        fs.readdirSync(graalBinDir).filter(file => nodeExecutables.includes(file)) :
                        fs.readdirSync(graalBinDir).filter(file => nodeExecutables.map(f => `${prefix}${f}`).includes(file));
                    taskLib.debug(`Resolved GraalVM Node binaries: ${resolvedNodeBinaries.join(", ")}`);
                    if (prefixNodeExecutables) {
                        resolvedNodeBinaries.forEach(f => {
                            const oldPath = path.join(graalBinDir, f);
                            const newPath = path.join(graalBinDir, `${prefix}${f}`);
                            taskLib.debug(`Renaming ${oldPath} to ${newPath}.`);
                            fs.renameSync(oldPath, newPath);
                        });
                    }
                    else {
                        resolvedNodeBinaries.forEach(f => {
                            const oldPath = path.join(graalBinDir, f);
                            const newPath = path.join(graalBinDir, f.substring(prefix.length));
                            taskLib.debug(`Renaming ${oldPath} to ${newPath}.`);
                            fs.renameSync(oldPath, newPath);
                        });
                    }
                }
                catch (err) {
                    throw `Error setting up GraalVM NodeJS executables prefix: Configured setting: prefixNodeExecutables := '${prefixNodeExecutables}'`;
                }
            });
        }
        function installGraalComponents(graalBinDir, components) {
            return __awaiter(this, void 0, void 0, function* () {
                const gu = path.join(graalBinDir, "gu");
                components.forEach(component => {
                    const args = ` -A -N install -n ${component}`;
                    taskLib.debug(`Installing GraalVM "${component}" component using ${gu}.`);
                    const result = taskLib.execSync(gu, args);
                    if (result.code != 0)
                        throw `Error installing GraalVM "${component}" component. ${result.stderr}`;
                });
            });
        }
        function components() {
            const pairs = [
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
        yield installGraalComponents(binDir, components());
        yield configureNodeExecutablesPrefix(binDir);
    });
}
function run(platform, arch, graalVersion, javaVersion, nativeImage, llvmToolchain, prefixNodeExecutables) {
    return __awaiter(this, void 0, void 0, function* () {
        try {
            if (toolLib.findLocalToolVersions(`graalvm-ce-java${javaVersion}`).includes(graalVersion.short())) {
                const resolvedPath = toolLib.findLocalTool(`graalvm-ce-java${javaVersion}`, graalVersion.short());
                const installedVersion = resolveInstalledVersion(resolveHome(resolvedPath, platform));
                if (installedVersion.full() === graalVersion.full()) {
                    taskLib.debug(`Using locally cached GraalVM ${graalVersion.full()} for Java ${javaVersion} from ${resolvedPath}.`);
                    yield configureLocalTool(resolvedPath, platform, nativeImage, llvmToolchain, prefixNodeExecutables);
                }
                else {
                    taskLib.debug(`Locally cached GraalVM ${installedVersion.full()} does not equal required ${graalVersion.full()}.`);
                    yield downloadGraalDistribution(platform, arch, graalVersion, javaVersion);
                    yield run(platform, arch, graalVersion, javaVersion, nativeImage, llvmToolchain, prefixNodeExecutables);
                }
            }
            else {
                yield downloadGraalDistribution(platform, arch, graalVersion, javaVersion);
                yield run(platform, arch, graalVersion, javaVersion, nativeImage, llvmToolchain, prefixNodeExecutables);
            }
        }
        catch (err) {
            taskLib.setResult(taskLib.TaskResult.Failed, `Error installing GraalVM ${graalVersion} for Java ${javaVersion} on ${arch}. ${err}`);
        }
    });
}
run(taskLib.getPlatform(), resolveArch(os.arch()), new GraalVersion(taskLib.getInput("graalVersion", true)), parseInt(taskLib.getInput("javaVersion", true), 10), taskLib.getBoolInput("nativeImage", true), taskLib.getBoolInput("llvmToolchain", true), taskLib.getBoolInput("prefixNodeExecutables", true));
