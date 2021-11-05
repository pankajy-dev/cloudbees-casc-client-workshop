/* eslint no-undef: 0 */

const path = require('path');
const MiniCSSExtractPlugin = require('mini-css-extract-plugin');
const RemoveEmptyScriptsPlugin = require('webpack-remove-empty-scripts');
const { CleanWebpackPlugin: CleanPlugin } = require('clean-webpack-plugin');

module.exports = {
  mode: 'development',
  entry: {
    "bundleVisualization": [
      path.join(__dirname, "src/main/less/bundleVisualization.less"),
      path.join(__dirname, "src/main/js/BundleVisualizationLink.js")
    ],
    "bundleVisualizationManagementLink": [
      path.join(__dirname, "src/main/less/bundleVisualizationManagementLink.less"),
    ],
  },
  output: {
    path: path.join(__dirname, "src/main/webapp/jsbundles"),
  },
  plugins: [
    new RemoveEmptyScriptsPlugin(),
    new MiniCSSExtractPlugin({
      filename: "[name].css",
    }),
    new CleanPlugin(),
  ],
  module: {
    rules: [
      {
        test: /\.(css|less)$/,
        use: [ MiniCSSExtractPlugin.loader, "css-loader", 'postcss-loader', "less-loader"]
      },
      {
        test: /\.js$/,
        exclude: /node_modules/,
        loader: "babel-loader",
      },
    ]
  }
}
