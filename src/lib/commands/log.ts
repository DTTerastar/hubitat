import { XmlEntities } from 'html-entities';
import { CommanderStatic } from 'commander';
import WebSocket from 'ws';
import chalk from 'chalk';

import { die } from '../common';
import { getHost } from '../request';
import { validateId } from '../resource';

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  program
    .command('log [type] [id]')
    .description(
      'Log events for a given source, type of source, or all sources'
    )
    .action((type: string, id?: string) => {
      const _type = validateType(type);
      const _id = validateId(id);

      if (_id && !_type) {
        die('An ID requires a type');
      }

      const ws = new WebSocket(`ws://${getHost()}/logsocket`);
      const entities = new XmlEntities();

      ws.on('close', () => {
        console.log('Closed connection to Hubitat');
      });

      ws.on('connectFailed', function(error) {
        console.log('Connect Error: ' + error.toString());
      });

      ws.on('open', () => {
        console.log('Opened connection to Hubitat');
      });

      ws.on('message', (data: string) => {
        const msg: Message = JSON.parse(data);
        if (_type && msg.type !== _type) {
          return;
        }
        if (_id && msg.id !== _id) {
          return;
        }
        logMessage(entities, msg);
      });
    });
}

function color(level: string)
{
  switch(level){
    case "trace":return chalk.grey;
    case "debug":return chalk.blueBright;
    case "warn":return chalk.yellowBright;
    case "error":return chalk.redBright;
    default: return chalk.magentaBright;
  }
}

function logMessage(entities: TextConverter, message: Message) {
  const { time, type, msg, level, id, name } = message;
  var levelId = `${level.slice(0,3)} [${type}:${String("0000" + id).slice(-4)}] ${name}`;
  console.log(color(level)(
    `${chalk.gray(time)} ${chalk.dim(levelId)} - ${entities.decode(msg)}`
  ));
}

function validateType(type: string): 'app' | 'dev' {
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/dev(ices)?/.test(type)) {
    return 'dev';
  }
  return <any>'';
}

interface Message {
  name: string;
  type: string;
  level: string;
  time: string;
  id: string;
  msg: string;
}

interface TextConverter {
  decode(input: string): string;
}
