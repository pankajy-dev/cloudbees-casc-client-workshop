 module.exports = {
  extends: "stylelint-config-standard",
  rules: {
    indentation: null,
    "comment-empty-line-before": null
  },
  // Keeps the default level to warn to avoid breaking the current
  // CI build environment
  defaultSeverity: "warning"
}