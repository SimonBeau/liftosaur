const fs = require("fs");
const path = require("path");
const vm = require("vm");

const root = path.resolve(__dirname, "..");
const bundlePath = path.join(root, "dist", "watch-bundle.js");
const fixturePath = path.join(root, "dist", "wear-spike-storage.json");

vm.runInThisContext(fs.readFileSync(bundlePath, "utf8"), { filename: "watch-bundle.js" });
if (!globalThis.Liftosaur) throw new Error("Real watch bundle did not expose globalThis.Liftosaur");

function call(method, ...args) {
  const response = JSON.parse(globalThis.Liftosaur[method](...args));
  if (!response.success) throw new Error(`${method}: ${response.error}`);
  return response.data;
}

if (process.argv[2] === "--recover") {
  const recoveredStorage = fs.readFileSync(process.argv[3], "utf8");
  const recovered = call("getProgress", recoveredStorage);
  const recoveredSets = recovered.exercises[0].sets;
  if (!recoveredSets[0].isCompleted || recoveredSets[0].completedWeight.value !== 135) {
    throw new Error("Fresh-process recovery lost the completed first set");
  }
  if (recoveredSets[1].weight.value !== 145) {
    throw new Error("Fresh-process recovery lost the Liftoscript-updated second set");
  }
  console.log("Fresh Node process recovered the active workout from persisted storage");
  process.exit(0);
}

let storage = fs.readFileSync(fixturePath, "utf8");
const validation = JSON.parse(globalThis.Liftosaur.validateStorage(storage));
if (!validation.success) throw new Error(`validateStorage: ${validation.error}`);

const recommendation = call("getNextHistoryRecord", storage);
if (recommendation.dayName !== "Engine Proof") throw new Error(`Unexpected day: ${recommendation.dayName}`);
if (recommendation.exercises[0].sets[0].weight.value !== 100) throw new Error("Unexpected initial weight");

storage = JSON.stringify(call("startWorkout", storage, "wear-spike-node"));
let progress = call("getProgress", storage);
const initialSet = progress.exercises[0].sets[0];
if (initialSet.reps !== 5 || initialSet.weight.value !== 100) throw new Error("Unexpected initial set state");

storage = JSON.stringify(call("updateSetWeight", storage, "wear-spike-node", 0, 0, 135));
storage = JSON.stringify(call("completeSet", storage, "wear-spike-node", 0, 0));
progress = call("getProgress", storage);
const first = progress.exercises[0].sets[0];
const second = progress.exercises[0].sets[1];
if (!first.isCompleted || first.completedWeight.value !== 135) throw new Error("Edited first set was not completed");
if (second.weight.value !== 145) throw new Error(`Liftoscript update failed: expected 145lb, got ${second.weight.value}`);

const next = call("getNextEntryAndSetIndex", storage, 0, 0);
if (next.entryIndex !== 0 || next.setIndex !== 1) throw new Error(`Unexpected next set: ${JSON.stringify(next)}`);

const recoveryPath = path.join(root, "dist", "wear-spike-recovery.json");
fs.writeFileSync(recoveryPath, storage);
try {
  require("child_process").execFileSync(process.execPath, [__filename, "--recover", recoveryPath], { stdio: "inherit" });
} finally {
  fs.rmSync(recoveryPath, { force: true });
}

console.log(JSON.stringify({
  bundleBytes: fs.statSync(bundlePath).size,
  day: recommendation.dayName,
  exercise: recommendation.exercises[0].name,
  initial: { reps: initialSet.reps, weight: initialSet.weight },
  completed: { weight: first.completedWeight, isCompleted: first.isCompleted },
  next: { index: next, reps: second.reps, weight: second.weight },
}, null, 2));
