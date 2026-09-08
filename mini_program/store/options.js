const { getOptions } = require("../api/options");
const { defaultOptions } = require("../data/config");

let cache = null;
let inflight = null;

function loadOptions() {
  if (cache) return Promise.resolve(cache);
  if (!inflight) {
    inflight = getOptions()
      .then((o) => {
        cache = o;
        return o;
      })
      .catch(() => defaultOptions)
      .then((o) => {
        inflight = null;
        return o;
      });
  }
  return inflight;
}

module.exports = { loadOptions };
