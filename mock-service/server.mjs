import http from 'node:http';
import { readFileSync } from 'node:fs';
const samples=JSON.parse(readFileSync(new URL('../test-data/mock-samples.json',import.meta.url)));
export const server=http.createServer((req,res)=>{
 const key=new URL(req.url,'http://localhost').pathname;
 if(key==='/api/acceptance'){res.writeHead(200,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({data:JSON.parse(readFileSync(new URL('../test-data/acceptance-http.json',import.meta.url)))}));return;}
 if(key==='/api/failure'){res.writeHead(503,{'Content-Type':'application/json'});res.end(JSON.stringify({error:'Simulated unavailable source'}));return;}
 if(key.startsWith('/business/')){res.writeHead(200,{'Content-Type':'text/plain; charset=utf-8'});res.end('本地模拟业务页面，仅用于深链接验收，不执行任何业务写操作。');return;}
 const data=key==='/health'?{status:'UP'}:samples[key.replace('/api/','')];
 res.writeHead(req.method!=='GET'?405:data?200:404,{'Content-Type':'application/json; charset=utf-8'});
 res.end(JSON.stringify(req.method!=='GET'?{error:'Method not allowed'}:data?{data,sample:true}:{error:'Not found'}));
});
if (process.argv[1]===new URL(import.meta.url).pathname) server.listen(Number(process.env.MOCK_PORT||4010),'127.0.0.1',()=>console.log('SCM Mock ready'));
