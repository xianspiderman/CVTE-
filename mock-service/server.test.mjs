import {test} from 'node:test';
import assert from 'node:assert/strict';
import {server} from './server.mjs';
test('HTTP fixture and missing routes',async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try {
  const url=`http://127.0.0.1:${server.address().port}`;
  const response=await fetch(url+'/api/procurement');
  assert.equal(response.status,200); assert.equal((await response.json()).data[0].id,'DEMO-procurement-001');
  assert.equal((await fetch(url+'/missing')).status,404);
 } finally { await new Promise(r=>server.close(r)); }
});
