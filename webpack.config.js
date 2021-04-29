const path = require('path');
const webpack = require('webpack');
const CopyWebpackPlugin = require("copy-webpack-plugin")

// function resolveConfiguration(mode) {
//     return {
//         entry: {
//             coursier: path.resolve(__dirname, "src", "coursier.ts")
//         },
//         target: 'node',
//         mode: mode,
//         module: {
//             rules: [{
//                 test: /\.tsx?$/,
//                 use: 'ts-loader',
//                 exclude: /node_modules/,
//             }],
//         },
//         resolve: {
//             extensions: ['.tsx', '.ts', '.js', '.json'],
//         },
//         output: {
//             libraryTarget: 'commonjs2',
//             filename: 'bundle.js',
//             path: path.resolve(__dirname, "dist", "tasks", "[name]")
//         },
//         node: {
//             __dirname: false,
//             __filename: false
//         }
//     }
// }

module.exports = (env) => {
    return {
        mode: env.production === true ? "production" : "development",
        target: "node",
        module: {
            rules: [{
                test: /\.tsx?$/,
                use: "ts-loader",
                exclude: /node_modules/
            }]
        },
        resolve: {
            extensions: [".tsx", ".ts", ".js", ".json"]
        },
        entry: {
            "setup-sbt": path.resolve(__dirname, "src", "tasks", "setup-sbt.ts"),
            "setup-graalvm": path.resolve(__dirname, "src", "tasks", "setup-graalvm.ts")
        },
        output: {
            libraryTarget: 'commonjs2',
            filename: '[name]/bundle.js',
            path: path.resolve(__dirname, "dist", "tasks"),
        },
        plugins: [
            new CopyWebpackPlugin({
                patterns: [
                    {
                        from: "**/*",
                        context: path.resolve(__dirname, "resources"),
                        to: path.resolve(__dirname, "dist")
                    },
                    {
                        from: "README.md",
                        to: path.resolve(__dirname, "dist")
                    },
                    {
                        from: "LICENSE",
                        to: path.resolve(__dirname, "dist")
                    },
                ]
            })

        ]
    };
};

