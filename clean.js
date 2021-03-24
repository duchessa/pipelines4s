const fs = require("fs");
const path = require('path');

const tasksDir = path.resolve(__dirname, 'tasks')

const tasks = fs.readdirSync(tasksDir);

function clean() {
    tasks.forEach(task => {
        const taskDir = path.join(tasksDir, task);
        console.info(`Cleaning ${task} task node_module directory.`);
        fs.rmdirSync(path.join(taskDir, "node_modules"), {recursive: true})

        console.info(`Cleaning ${task} compiled scripts.`)
        fs.readdirSync(taskDir).forEach(file => {
            if (file.endsWith(".js")) fs.rmSync(path.join(taskDir, file), {recursive: true})
        })
    })

    console.info("Cleaning root project node_modules and artefacts.")
    fs.readdirSync(__dirname).forEach(file => {
        if (file === "node_modules" || file.endsWith(".vsix")) fs.rmSync(path.join(__dirname, file), {recursive: true})
    })
}

clean()