const { api, qs } = require("./client");

const DEFAULT_SIZE = 100;

const generate = (sel, count, promptOverride) =>
  api.post("/scripts/generate", {
    sel,
    count,
    promptOverride: promptOverride == null ? null : promptOverride,
  });

const listScripts = (query) => {
  const q = query || {};
  return api.get(
    "/scripts" +
      qs({
        topicType: q.topicType,
        scriptType: q.scriptType,
        page: q.page == null ? 1 : q.page,
        size: q.size == null ? DEFAULT_SIZE : q.size,
      }),
  );
};

const getScript = (id) => api.get("/scripts/" + id);
const deleteScript = (id) => api.del("/scripts/" + id);

module.exports = { generate, listScripts, getScript, deleteScript };
