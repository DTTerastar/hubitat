import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { basename, join, relative } from 'path';
import { createHash } from 'crypto';
import * as cheerio from 'cheerio';
import { execSync } from 'child_process';
import fetch from 'node-fetch';
import chalk from 'chalk';

import {
  Context,
  CodeResource,
  die,
  ResourceType,
  Logger,
  createLogger,
  getResources,
  simpleEncode,
  trim,
  validateId
} from './common';
import { CommanderStatic } from 'commander';

import path = require('path');
import fs = require('fs');

const manifestFile = join(__dirname, '..', 'manifest.json');
const resourceDirs = {
  app: relative(process.cwd(), join(__dirname, '..', 'apps')),
  driver: relative(process.cwd(), join(__dirname, '..', 'drivers'))
};
const repoDir = '.repos';

let program: CommanderStatic;
let hubitatHost: string;
let log: Logger;

// Setup cli ------------------------------------------------------------------

export default function init(context: Context) {
  program = context.program;
  hubitatHost = context.hubitatHost;
  log = createLogger(program);

  // Install a script from a github repo
  program
    .command('install <type> <path>')
    .description(
      'Install a resource from a GitHub path ' +
        '(git:org/repo/file.groovy) or local file path'
    )
    .action(async (type, path) => {
      let filename: string;

      if (/^git:/.test(path)) {
        if (!/[^/]+\/.*\.groovy$/.test(path)) {
          die('path must have format git:org/repo/path/to/file.groovy');
        }
  
        const gitPath = path.slice(4);
        const parts = gitPath.split('/');
        const orgPath = join(repoDir, parts[0]);
        mkdirp(orgPath);

        const repoPath = join(orgPath, parts[1]);
        if (!existsSync(repoPath)) {
          const repo = parts.slice(0, 2).join('/');
          execSync(`git clone https://github.com/${repo}`, { cwd: orgPath });
        }

        filename = join(repoDir, gitPath);
      } else {
        filename = path;
      }

      try {
        const rtype = validateCodeType(type);
        const localManifest = loadManifest();

        await createRemoteResource(rtype, filename, localManifest);
        saveManifest(localManifest);
      } catch (error) {
        die(error);
      }
    });

  // Pull a specific resource from Hubitat
  program
    .command('pull [type] [id]')
    .description('Pull drivers and apps from Hubitat to this repo')
    .action(async (type, id) => {
      try {
        let rtype: CodeResourceType | undefined;
        if (type) {
          rtype = validateCodeType(type);
        }
        if (id) {
          validateId(id);
        }

        // The remote manifest will be used for filenames (if files don't exist
        // locally)
        const remoteManifest = await getRemoteManifest();
        const localManifest = loadManifest();

        if (!rtype) {
          console.log(chalk.green('Pulling everything...'));
          await Promise.all(
            ['app', 'driver'].map(async typeStr => {
              const type = <keyof Manifest>typeStr;
              await Promise.all(
                Object.keys(remoteManifest[type]).map(async id => {
                  await updateLocalResource(
                    type,
                    Number(id),
                    localManifest,
                    remoteManifest
                  );
                })
              );
            })
          );
        } else if (!id) {
          console.log(`Pulling all ${rtype}s...`);
          await Promise.all(
            Object.keys(remoteManifest[rtype]).map(async id => {
              updateLocalResource(
                rtype!,
                Number(id),
                localManifest,
                remoteManifest
              );
            })
          );
        } else {
          console.log(`Pulling ${type}:${id}...`);
          updateLocalResource(type, id, localManifest, remoteManifest);
        }

        saveManifest(localManifest);
      } catch (error) {
        die(error);
      }
    });

  // Push a specific resource to Hubitat
  program
    .command('push [type] [id]')
    .description('Push apps and drivers from this repo to Hubitat')
    .action(async (type, id) => {
      let rtype: keyof Manifest | undefined;

      try {
        if (type) {
          rtype = validateCodeType(type);
        }
        if (id) {
          validateId(id);
        }

        const remoteManifest = await getRemoteManifest();
        const localManifest = loadManifest();

        if (!rtype) {
          console.log(chalk.green('Pushing everything...'));
          await Promise.all(
            ['app', 'driver'].map(async typeStr => {
              const type = <CodeResourceType>typeStr;
              await Promise.all(
                Object.keys(localManifest[type]).map(async id => {
                  await updateRemoteResource(
                    type,
                    Number(id),
                    localManifest,
                    remoteManifest
                  );
                })
              );
            })
          );
        } else if (!id) {
          console.log(chalk.green(`Pushing all ${rtype}...`));
          await Promise.all(
            Object.keys(localManifest[rtype]).map(async id => {
              await updateRemoteResource(
                rtype!,
                Number(id),
                localManifest,
                remoteManifest
              );
            })
          );
        } else {
          console.log(chalk.green(`Pushing ${type}:${id}...`));
          await updateRemoteResource(type, id, localManifest, remoteManifest);
        }

        saveManifest(localManifest);
      } catch (error) {
        die(error);
      }
    });
}

// Implementation -------------------------------------------------------------

/**
 * Create a remote resource. This should return a new version number which will
 * be added to the manifest.
 */
async function createRemoteResource(
  type: CodeResourceType,
  filename: string,
  localManifest: Manifest,
  isGithubResource = false
): Promise<boolean> {
  const source = readFileSync(filename, {
    encoding: 'utf8'
  });

  const hash = hashSource(source);
  console.log(chalk.green(`Creating ${type} ${filename}...`));
  const newRes = await postResource(type, source);
  let newEntry: ManifestEntry;

  if (isGithubResource) {
    newEntry = {
      hash,
      filename,
      id: newRes.id,
      version: 1
    };
  } else {
    const resources = await getResources(hubitatHost, type);
    const resource = resources.find(res => res.id === newRes.id)!;
    newEntry = {
      hash,
      filename: getFilename(resource),
      ...newRes
    };
  }

  localManifest[type][newRes.id] = toManifestEntry(newEntry);

  return true;
}

/**
 * Get a manifest of resources available on the Hubitat
 */
async function getRemoteManifest(type?: CodeResourceType): Promise<Manifest> {
  const manifest: Manifest = {
    app: {},
    driver: {}
  };

  if (type) {
    const resources = await getFileResources(type);
    manifest[type] = toManifestSection(resources);
  } else {
    const apps = await getFileResources('app');
    manifest.app = toManifestSection(apps);
    const drivers = await getFileResources('driver');
    manifest.driver = toManifestSection(drivers);
  }

  return manifest;
}

/**
 * Update a local resource with a remote resource. This saves a local copy of
 * the resource and updates the manifest. If the remote resource is newer than
 * the local resource, it will overwrite the local resource. If the local
 * resource has been edited, it will need to be committed before a pull can
 * complete.
 *
 * After a pull, any files that differ between the remote and local will result
 * in unstaged changes in the local repo.
 */
async function updateLocalResource(
  type: CodeResourceType,
  id: number,
  localManifest: Manifest,
  remoteManifest: Manifest
): Promise<boolean> {
  const resource = await getResource(type, id);
  const localRes = localManifest[type][resource.id];

  if (localRes && localRes.filename.indexOf(repoDir) === 0) {
    console.log(chalk.yellow(`Skipping github resource ${basename(localRes.filename)}`));
    return false;
  }

  const remoteRes = remoteManifest[type][resource.id];
  const filename = join(resourceDirs[type], remoteRes.filename);

  if (localRes && existsSync(filename)) {
    const source = readFileSync(filename, { encoding: 'utf8' });
    const sourceHash = hashSource(source);
    // If the local has changed from the last time it was synced with Hubitat
    // *and* it hasn't been committed, don't update
    if (sourceHash !== localRes.hash && needsCommit(filename)) {
      console.log(chalk.red(`Skipping ${filename}; please commit first`));
      return false;
    }
  }

  if (localRes && existsSync(filename) && remoteRes.hash === localRes.hash) {
    console.log(chalk.yellow(`Skipping ${filename}; no changes`));
    return true;
  }

  console.log(chalk.green(`Updating ${type} ${filename}`));
  var path = join(resourceDirs[type], remoteRes.filename);
  ensureDirectoryExistence(path)
  writeFileSync(path, resource.source);

  const hash = hashSource(resource.source);
  const newResource = { type, hash, filename: remoteRes.filename, ...resource };
  localManifest[type][resource.id] = toManifestEntry(newResource);

  return true;
}

function ensureDirectoryExistence(filePath: string) {
  var dirname = path.dirname(filePath);
  if (fs.existsSync(dirname)) {
    return true;
  }
  ensureDirectoryExistence(dirname);
  fs.mkdirSync(dirname);
}

/**
 * Update a remote resource. This should return a new version number which will
 * be added to the manifest.
 */
async function updateRemoteResource(
  type: CodeResourceType,
  id: number,
  localManifest: Manifest,
  remoteManifest: Manifest
): Promise<boolean> {
  const localRes = localManifest[type][id];
  const remoteRes = remoteManifest[type][id];
  const filename = localRes.filename;
  let source: string;

  try {
    if (filename.indexOf(repoDir) === 0) {
      const repo = join(...filename.split('/').slice(0, 3));
      console.log(`Updating github resource ${basename(filename)}`);
      execSync('git pull', { cwd: repo });
      source = readFileSync(filename, {
        encoding: 'utf8'
      });
      // The local version is irrelevant for repo-based resources
      localRes.version = remoteRes.version;
    } else {
      source = readFileSync(join(resourceDirs[type], filename), {
        encoding: 'utf8'
      });
    }

    const hash = hashSource(source);
    if (hash === localRes.hash && localRes.version == remoteRes.version) {
      // File hasn't changed -- don't push
      log(chalk.yellow(`${filename} hasn't changed; not pushing`));
      return true;
    }

    if (localRes.version !== remoteRes.version) {
      console.log(chalk.red(`${type} ${filename} is out of date; pull first`));
      return false;
    }

    if (source.length>300*1024) {
      log(chalk.yellow(`${filename} is TOO large; not pushing`));
      return true; 
    }

    console.log(chalk.green(`Pushing ${type} ${filename}...`));
    const res = await putResource(type, id, localRes.version, source);
    if (res.status === 'error') {
      console.error(
        chalk.red(`Error pushing ${type} ${filename}: ${trim(res.errorMessage)}`)
      );
      return false;
    }

    const newResource = {
      hash,
      filename,
      id: res.id,
      version: res.version
    };
    localManifest[type][res.id] = toManifestEntry(newResource);
  } catch (err) {
    if (err.code !== 'ENOENT') throw err;
    console.log(`No local script ${filename}, removing from manifest`);
    delete localManifest[type][id];
  }

  return true;
}

/**
 * Indicate whether a file has uncommitted changes
 */
function needsCommit(file: string) {
  if (!existsSync(file)) {
    return false;
  }
  return execSync(`git status --short ${file}`, { encoding: 'utf8' }) !== '';
}

/**
 * Create a manifest section representing an array of FileResources
 */
function toManifestSection(resources: FileResource[]) {
  return resources.reduce(
    (all, res) => ({
      ...all,
      [res.id]: toManifestEntry(res)
    }),
    <ManifestResources>{}
  );
}

/**
 * Create a manifest entry representing a FileResource
 */
function toManifestEntry(resource: ManifestEntry) {
  return {
    filename: resource.filename,
    id: resource.id,
    version: resource.version,
    hash: resource.hash
  };
}

/**
 * Get a filename for a resource
 */
function getFilename(resource: CodeResource) {
  const { name, namespace } = resource;
  return `${namespace!.toLowerCase()}/${name!.toLowerCase().replace(/\s/g, '-')}.groovy`;
}

/**
 * Get a resource list from Hubitat
 */
async function getFileResources(
  type: CodeResourceType
): Promise<FileResource[]> {
  const resources = await getResources(hubitatHost, type);

  return Promise.all(
    resources.map(async res => {
      const { id } = res;
      const filename = getFilename(res);
      const item = await getResource(type, Number(id));
      const hash = hashSource(item.source);
      return { filename, hash, type, ...item };
    })
  );
}

/**
 * Retrieve a specific resource (driver or app)
 */
async function getResource(
  type: ResourceType,
  id: number
): Promise<ResponseResource> {
  const response = await fetch(
    `http://${hubitatHost}/${type}/ajax/code?id=${id}`
  ); 
  if (response.status !== 200) {
    throw new Error(`Error getting ${type} ${id}: ${response.statusText}`);
  }
  return await response.json<ResponseResource>();
}

/**
 * Create a specific resource (driver or app)
 */
async function postResource(
  type: ResourceType,
  source: string
): Promise<CreateResource> {
  const url = `http://${hubitatHost}/${type}/save`;
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: simpleEncode({ id: '', version: '', source })
  });
  if (response.status !== 200) {
    throw new Error(`Error creating ${type}: ${response.statusText}`);
  }

  const html = await response.text();
  const $ = cheerio.load(html);

  if (response.url === url) {
    // URL didn't transition meaning code wasn't saved
    const errors = $('#errors');
    throw new Error(`Error creating ${type}: ${errors.text().trim()}`);
  }

  const form = $('form[name="editForm"]');
  const id = $(form)
    .find('input[name="id"]')
    .val();
  const version = $(form)
    .find('input[name="version"]')
    .val();

  return {
    id: Number(id),
    version: Number(version)
  };
}

/**
 * Store a specific resource (driver or app)
 */
async function putResource(
  type: ResourceType,
  id: number,
  version: number,
  source: string
): Promise<ResponseResource> {
  const response = await fetch(`http://${hubitatHost}/${type}/ajax/update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: simpleEncode({ id, version, source })
  });
  if (response.status !== 200) {
    throw new Error(`Error putting ${type} ${id}: ${response.statusText}`);
  }
  return response.json<ResponseResource>();
}

/**
 * Load the current manifest file
 */
function loadManifest(): Manifest {
  try {
    const data = readFileSync(manifestFile, { encoding: 'utf8' });
    if (data) {
      return JSON.parse(data);
    }
  } catch (error) {
    if (error.code !== 'ENOENT') {
      throw error;
    }
  }

  return {
    app: {},
    driver: {}
  };
}

/**
 * Make a directory and its parents
 */
function mkdirp(dir: string) {
  const parts = dir.split('/');
  let path = '.';
  while (parts.length > 0) {
    path = join(path, parts.shift()!);
    if (!existsSync(path)) {
      mkdirSync(path);
    }
  }
}

/**
 * Save the given manifest, overwriting the current manifest
 */
function saveManifest(manifest: Manifest) {
  return writeFileSync(manifestFile, JSON.stringify(manifest, null, '  '));
}

/**
 * Generate a SHA512 hash of a source string
 */
function hashSource(source: string) {
  const hash = createHash('sha512');
  hash.update(source);
  return hash.digest('hex');
}

function validateCodeType(type: string): CodeResourceType {
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/drivers?/.test(type)) {
    return 'driver';
  }

  die(`Invalid type "${type}"`);
  return <CodeResourceType>'';
}

interface Manifest {
  app: ManifestResources;
  driver: ManifestResources;
}

interface ManifestResources {
  [id: number]: ManifestEntry;
}

interface ManifestEntry {
  id: number;
  filename: string;
  version: number;
  hash: string;
}

type CodeResourceType = 'app' | 'driver';

interface CreateResource {
  id: number;
  version: number;
}

interface ResponseResource {
  id: number;
  version: number;
  source: string;
  status: string;
  errorMessage?: string;
}

interface FileResource extends ResponseResource {
  filename: string;
  hash: string;
  type: ResourceType;
}
