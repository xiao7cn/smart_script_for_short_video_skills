const { api } = require("./client");

const getTask = (taskNo) => api.get("/tasks/" + taskNo);

module.exports = { getTask };
