const { api } = require("./client");

const getPersona = () => api.get("/persona");
const savePersona = (persona) => api.put("/persona", persona);
const resetPersona = () => api.post("/persona/reset");

module.exports = { getPersona, savePersona, resetPersona };
