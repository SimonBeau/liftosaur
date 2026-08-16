const configs = require("./webpack.config.js");

// Keep the Wear build independent of the web and editor outputs while reusing
// the exact same watch entry, plugins, aliases, and optimization settings.
module.exports = configs[1];
