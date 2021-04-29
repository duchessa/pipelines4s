const fs = require("fs");
const path = require('path');

function clean() {
    console.info("Cleaning root project node_modules and artefacts.")
    fs.readdirSync(__dirname).forEach(file => {
        if (file === "dist" || file === "node_modules" || file.endsWith(".vsix"))
            fs.rmSync(path.join(__dirname, file), {recursive: true})
    })
}

clean()