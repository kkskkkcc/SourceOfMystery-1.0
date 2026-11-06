// Re-import one gear asset for review. risky_eval:
//   (()=>{const f=require('fs');return Function('id','kind','opts','require',
//     f.readFileSync('I:/Other_Dev/SourceOfMystery/tools/gear/review.js','utf8'))('divine_blessing','armor',{},require)})()
const fs = require('fs');
const root = 'I:/Other_Dev/SourceOfMystery/';
const a = root + 'src/main/resources/assets/sourceofmystery/';
const folder = kind === 'armor' ? 'armor' : 'item';
const args = Object.assign({
    geo: a + 'geo/' + folder + '/' + id + '.geo.json',
    texture: a + 'textures/' + folder + '/' + id + '.png',
    name: id, mannequin: kind === 'armor', closeOthers: true
}, opts || {});
return Function('args', 'require', fs.readFileSync(root + 'tools/gear/bb_import.js', 'utf8'))(args, require);
