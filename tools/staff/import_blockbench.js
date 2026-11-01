// Run through Blockbench MCP risky_eval with the native bindings shown in docs/staff-model-v2.md.
// Imports the runtime geometry into the active empty/staff project with one Undo entry.
const fs = require('fs');
const assets = root + '/src/main/resources/assets/sourceofmystery/';
const model = JSON.parse(fs.readFileSync(assets + 'geo/item/dimension_breaker_staff.geo.json', 'utf8'))['minecraft:geometry'][0];
Undo.initEdit({elements: Cube.all.slice(), outliner: true, textures: Texture.all.slice(), animations: Animation.all.slice()});
Group.all.slice().filter(group => group.parent === 'root').forEach(group => group.remove());
Animation.all.slice().forEach(animation => animation.remove(false));
const texture = Texture.all[0] || new Texture({name: 'dimension_breaker_staff.png'}).add(false);
texture.fromDataURL('data:image/png;base64,' + fs.readFileSync(assets + 'textures/item/dimension_breaker_staff.png').toString('base64'));
Project.texture_width = model.description.texture_width;
Project.texture_height = model.description.texture_height;
Project.geometry_name = model.description.identifier.replace(/^geometry\./, '');
Project.box_uv = false;
texture.uv_width = model.description.texture_width;
texture.uv_height = model.description.texture_height;
Project.visible_box = [model.description.visible_bounds_width, model.description.visible_bounds_height, model.description.visible_bounds_offset[1]];
const groups = {};
const mirror = v => [-v[0], v[1], v[2]];
const rotation = v => [-v[0], -v[1], v[2]];
for (const part of model.bones) {
    const group = new Group({name: part.name, origin: mirror(part.pivot || [0,0,0]), rotation: rotation(part.rotation || [0,0,0])});
    if (part.parent) group.addTo(groups[part.parent]);
    group.init();
    groups[part.name] = group;
    for (const [index, element] of (part.cubes || []).entries()) {
        const p = element.origin, size = element.size;
        const cube = new Cube({name: part.name + '_' + index,
            from: [-p[0]-size[0], p[1], p[2]], to: [-p[0], p[1]+size[1], p[2]+size[2]],
            origin: mirror(element.pivot || [0,0,0]), rotation: rotation(element.rotation || [0,0,0]), box_uv: false
        }).addTo(group).init();
        for (const side of Object.keys(cube.faces)) {
            const face = element.uv[side];
            let uv = [0,0,0,0];
            if (face) {
                const [u,v] = face.uv, [w,h] = face.uv_size;
                uv = side === 'up' || side === 'down' ? [u+w,v+h,u,v] : [u,v,u+w,v+h];
            }
            cube.faces[side].extend({uv, texture: face ? texture.uuid : null});
        }
    }
}
AnimationCodec.codecs.bedrock.loadFile({content: fs.readFileSync(assets + 'animations/item/dimension_breaker_staff.animation.json', 'utf8'),
    path: assets + 'animations/item/dimension_breaker_staff.animation.json'});
Undo.finishEdit('Refine silver staff v2', {elements: Cube.all, textures: Texture.all, animations: Animation.all, outliner: true});
Project.name = 'dimension_breaker_staff_v2';
Canvas.updateAll();
return {cubes: Cube.all.length, groups: Group.all.length, animations: Animation.all.length};
