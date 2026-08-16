import fs from "fs";
import path from "path";
import { Storage_fillVersions, Storage_getDefault, Storage_validateStorage } from "../src/models/storage";
import { PlannerProgram_evaluateText } from "../src/pages/planner/models/plannerProgram";
import { IProgram } from "../src/types";

export const WEAR_SPIKE_PROGRAM_TEXT = `# Week 1
## Engine Proof
Squat / 2x5 / 100lb / warmup: none / update: custom() {~
  if (setIndex == 1) {
    weights[2] = completedWeights[1] + 10lb
  }
~}`;

const program: IProgram = {
  vtype: "program",
  id: "wear-spike-program",
  name: "Wear Engine Spike",
  description: "Deterministic offline Wear JavaScript engine fixture",
  shortDescription: "Wear engine fixture",
  url: "",
  author: "Liftosaur",
  tags: [],
  exercises: [],
  days: [],
  weeks: [],
  nextDay: 1,
  isMultiweek: false,
  planner: {
    vtype: "planner",
    name: "Wear Engine Spike",
    weeks: PlannerProgram_evaluateText(WEAR_SPIKE_PROGRAM_TEXT),
  },
};

const defaults = Storage_getDefault();
const storage = Storage_fillVersions(
  {
    ...defaults,
    id: 1700000000000,
    originalId: 1700000000000,
    tempUserId: "wearspike1",
    currentProgramId: program.id,
    programs: [program],
    progress: [],
    history: [],
  },
  "wear-fixture-generator"
);

const validation = Storage_validateStorage(storage);
if (!validation.success) {
  throw new Error(`Generated Wear fixture is invalid: ${validation.error.join("; ")}`);
}

const outputDir = path.resolve(__dirname, "../dist");
fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, "wear-spike-storage.json"), JSON.stringify(storage));
console.log(`Generated Wear storage fixture (${JSON.stringify(storage).length} bytes)`);
