// Blockbench MCP importer for tools/gear outputs. Run through risky_eval:
//   Function('args','require',src)(args, require)
// args = {geo, texture, animation?, name, save?, mannequin?}
// Creates a new Bedrock project, rebuilds bones, cubes and per-face UVs, saves the
// editable .bbmodel, then optionally adds an unsaved grey player for fit review.
const fs = require('fs');
const geo = JSON.parse(fs.readFileSync(args.geo, 'utf8'))['minecraft:geometry'][0];
if (args.closeOthers) ModelProject.all.slice().forEach(p => { if (p.name === args.name) p.close(true); });
newProject(Formats.bedrock);
const d = geo.description;
Project.name = args.name;
Project.geometry_name = d.identifier.replace(/^geometry\./, '');
Project.texture_width = d.texture_width;
Project.texture_height = d.texture_height;
Project.box_uv = false;
const texture = new Texture({name: args.name + '.png'}).add(false);
texture.fromDataURL('data:image/png;base64,' + fs.readFileSync(args.texture).toString('base64'));
texture.uv_width = d.texture_width;
texture.uv_height = d.texture_height;
const groups = {};
const mirror = v => [-v[0], v[1], v[2]];
const rot = v => [-v[0], -v[1], v[2]];
for (const bone of geo.bones) {
    const group = new Group({name: bone.name, origin: mirror(bone.pivot || [0, 0, 0]), rotation: rot(bone.rotation || [0, 0, 0])});
    if (bone.parent) group.addTo(groups[bone.parent]);
    group.init();
    groups[bone.name] = group;
    (bone.cubes || []).forEach((c, i) => {
        const p = c.origin, s = c.size;
        const cube = new Cube({name: bone.name + '_' + i, from: [-p[0] - s[0], p[1], p[2]], to: [-p[0], p[1] + s[1], p[2] + s[2]],
            origin: mirror(c.pivot || [0, 0, 0]), rotation: rot(c.rotation || [0, 0, 0]), box_uv: false}).addTo(group).init();
        for (const side of Object.keys(cube.faces)) {
            const f = c.uv[side];
            if (!f) { cube.faces[side].extend({uv: [0, 0, 0, 0], texture: null}); continue; }
            const [u, v] = f.uv, [w, h] = f.uv_size;
            const uv = side === 'up' || side === 'down' ? [u + w, v + h, u, v] : [u, v, u + w, v + h];
            cube.faces[side].extend({uv, texture: texture.uuid});
        }
    });
}
if (args.animation && fs.existsSync(args.animation)) {
    AnimationCodec.codecs.bedrock.loadFile({content: fs.readFileSync(args.animation, 'utf8'), path: args.animation});
}
Canvas.updateAll();
if (args.save) {
    fs.writeFileSync(args.save, Codecs.project.compile({editor_state: false}));
}
if (args.mannequin) {
    const skin = new Texture({name: 'mannequin'}).add(false);
    const cv = document.createElement('canvas'); cv.width = cv.height = 16;
    const ctx = cv.getContext('2d'); ctx.fillStyle = '#9aa0a6'; ctx.fillRect(0, 0, 16, 16);
    ctx.fillStyle = '#868c92'; ctx.fillRect(0, 12, 16, 4);
    skin.fromDataURL(cv.toDataURL()); skin.uv_width = 16; skin.uv_height = 16;
    const ref = new Group({name: 'reference_mannequin', origin: [0, 0, 0]}).init();
    const parts = args.mannequinPose || {};
    const add = (name, from, to, origin, rotation) => {
        const g = new Group({name: 'ref_' + name, origin, rotation: rotation || [0, 0, 0]}).addTo(ref).init();
        const c = new Cube({name: 'ref_' + name, from, to, origin, box_uv: false}).addTo(g).init();
        for (const side of Object.keys(c.faces)) c.faces[side].extend({uv: [0, 0, 16, 16], texture: skin.uuid});
    };
    if (!args.noHead) add('head', [-4, 24, -4], [4, 32, 4], [0, 24, 0], parts.head);
    add('body', [-4, 12, -2], [4, 24, 2], [0, 24, 0], parts.body);
    add('right_arm', [4, 12, -2], [8, 24, 2], [5, 22, 0], parts.rightArm);
    add('left_arm', [-8, 12, -2], [-4, 24, 2], [-5, 22, 0], parts.leftArm);
    add('right_leg', [0, 0, -2], [4, 12, 2], [2, 12, 0], parts.rightLeg);
    add('left_leg', [-4, 0, -2], [0, 12, 2], [-2, 12, 0], parts.leftLeg);
    Canvas.updateAll();
}
return {cubes: Cube.all.length, groups: Group.all.length, atlas: [d.texture_width, d.texture_height]};
