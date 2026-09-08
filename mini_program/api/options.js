const { api } = require("./client");

const getOptions = () => api.get("/options");

module.exports = { getOptions };
