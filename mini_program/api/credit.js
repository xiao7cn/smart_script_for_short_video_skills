const { api } = require("./client");

const getCredits = () => api.get("/credits");
const getPacks = () => api.get("/credits/packs");
const recharge = (packId) => api.post("/credits/recharge", { packId });

module.exports = { getCredits, getPacks, recharge };
