import { cp, mkdir, readdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const catalogPath = path.join(__dirname, "catalog-data.json");
const mediaDir = path.join(__dirname, "media");
const apksDir = path.join(__dirname, "apks");
const outRoot = path.join(__dirname, "static-site");
const outBackend = path.join(outRoot, "backend");
const outAppsDir = path.join(outBackend, "apps");
const outPlaceholdersDir = path.join(outBackend, "media", "placeholders");

const BASE_URL = (process.env.STATIC_BASE_URL || "https://ct195932.tw1.ru/backend").replace(/\/$/, "");

async function exists(p) {
  try {
    await stat(p);
    return true;
  } catch {
    return false;
  }
}

async function findFirstFileByPrefix(dir, prefix) {
  if (!(await exists(dir))) return null;
  const files = await readdir(dir);
  const found = files.find((f) => f.startsWith(prefix));
  return found || null;
}

async function listFilesByPrefix(dir, prefix) {
  if (!(await exists(dir))) return [];
  const files = await readdir(dir);
  return files.filter((f) => f.startsWith(prefix)).sort((a, b) => a.localeCompare(b));
}

function placeholderIcon() {
  return `${BASE_URL}/media/placeholders/icon.svg`;
}

function placeholderShots() {
  return [
    `${BASE_URL}/media/placeholders/screen.svg`,
    `${BASE_URL}/media/placeholders/screen.svg`,
    `${BASE_URL}/media/placeholders/screen.svg`,
  ];
}

async function main() {
  const raw = JSON.parse(await readFile(catalogPath, "utf-8"));
  const apps = raw.APPS || [];

  await rm(outRoot, { recursive: true, force: true });
  await mkdir(outAppsDir, { recursive: true });
  await mkdir(outPlaceholdersDir, { recursive: true });

  if (await exists(mediaDir)) {
    await cp(mediaDir, path.join(outBackend, "media"), { recursive: true, force: true });
  }
  if (await exists(apksDir)) {
    await cp(apksDir, path.join(outBackend, "apks"), { recursive: true, force: true });
  }

  const iconSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><rect width="512" height="512" fill="#D3D6DB"/><rect x="118" y="118" width="276" height="276" rx="42" fill="#9CA3AF"/><path d="M170 322l58-74 44 52 30-38 40 60H170z" fill="#E5E7EB"/></svg>`;
  const screenSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920"><rect width="1080" height="1920" fill="#D3D6DB"/><rect x="120" y="280" width="840" height="1360" rx="56" fill="#9CA3AF"/><path d="M220 1320l170-220 140 160 120-150 210 310H220z" fill="#E5E7EB"/></svg>`;
  await writeFile(path.join(outPlaceholdersDir, "icon.svg"), iconSvg, "utf-8");
  await writeFile(path.join(outPlaceholdersDir, "screen.svg"), screenSvg, "utf-8");

  const list = [];
  for (const app of apps) {
    const appId = String(app.id);
    const result = { ...app };
    const localApkPath = path.join(apksDir, `${appId}.apk`);
    result.apkUrl = (await exists(localApkPath)) ? `${BASE_URL}/apks/${appId}.apk` : null;

    const iconFile = await findFirstFileByPrefix(path.join(mediaDir, "remote"), `${appId}_icon_0`);
    if (iconFile) {
      result.iconUrl = `${BASE_URL}/media/remote/${iconFile}`;
    } else {
      result.iconUrl = placeholderIcon();
    }

    const shotFiles = await listFilesByPrefix(path.join(mediaDir, "remote"), `${appId}_shot_`);
    if (shotFiles.length > 0) {
      result.screenshots = shotFiles.map((f) => `${BASE_URL}/media/remote/${f}`);
    } else {
      result.screenshots = placeholderShots();
    }

    list.push(result);
    await writeFile(path.join(outAppsDir, `${appId}.json`), JSON.stringify(result, null, 2), "utf-8");
  }

  await writeFile(path.join(outBackend, "apps.json"), JSON.stringify(list, null, 2), "utf-8");
  await writeFile(
    path.join(outRoot, "DEPLOY.txt"),
    `Upload content of this folder to hosting root.\nExpected URLs:\n${BASE_URL}/apps.json\n${BASE_URL}/apps/1.json\n`,
    "utf-8"
  );

  console.log(`Static site generated: ${outRoot}`);
  console.log(`Apps: ${list.length}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
