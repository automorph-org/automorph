const simpleJsonRpc = require('./simple-jsonrpc-js.min.js');

var jrpc = simpleJsonRpc.connect_xhr('http://localhost:9000/api');

jrpc.call('hello', {n: 1}).then(function(res) {
    console.log("Answer:", res);
});

console.log("done!");

