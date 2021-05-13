const merge = require("webpack-merge");


module.exports = merge(require('./scalajs.webpack.config'), {
    target: "node",
    output: {
        libraryTarget: 'commonjs2'
    },
    node: {
        __dirname: false,
        __filename: false,
    },
});


