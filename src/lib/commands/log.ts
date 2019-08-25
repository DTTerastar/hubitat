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

function color(level: string){
  switch(level){
    case "info":return chalk.yellow;
    case "debug":return chalk.blueBright;
    case "trace":return chalk.blue;
    case "warn":return chalk.red;
    case "error":return chalk.redBright;
    default: return chalk.white;
  }
}

function logMessage(entities: TextConverter, message: Message) {
  const { time, type, msg, level, id, name } = message;
  console.log(color(level)(
    `${chalk.gray(time)} ${level.slice(0,3)} [${chalk.magenta(type)}:${chalk.white(String("0000" + id).slice(-4))}] ${chalk.white(name)} - ${entities.decode(msg)}`
  ));
}

function validateType(type: string): 'app' | 'dev' {
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/dev(ices)?/.test(type)) {
    return 'dev';
  }
  die('Type should be "app" or "dev"');
  return <any>'';
}

interface Message {
  name: string;
  type: string;
  level: string;
  time: string;
  id: number;
  msg: string;
}

interface TextConverter {
  decode(input: string): string;
}
