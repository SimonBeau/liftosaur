const fs = require("fs");
const path = require("path");

process.env.NODE_ENV = "production";

const webpack = require("webpack");
const config = require("../webpack.watch.config.js");
const outputDirectory = config.output.path;

fs.mkdirSync(outputDirectory, { recursive: true });
for (const name of fs.readdirSync(outputDirectory)) {
  if (name.endsWith(".watch-bundle.js") || name === "watch-bundle.js") {
    fs.rmSync(path.join(outputDirectory, name));
  }
}

webpack(config, (error, stats) => {
  if (error) throw error;
  const output = stats.toString({ colors: true, chunks: false, modules: false });
  if (output) console.log(output);
  if (stats.hasErrors()) process.exitCode = 1;

  const emittedJavaScript = fs.readdirSync(outputDirectory).filter(
    (name) => name === "watch-bundle.js" || name.endsWith(".watch-bundle.js")
  );
  if (emittedJavaScript.length !== 1 || emittedJavaScript[0] !== "watch-bundle.js") {
    throw new Error(`Wear requires one self-contained JS bundle; emitted: ${emittedJavaScript.join(", ")}`);
  }
});
