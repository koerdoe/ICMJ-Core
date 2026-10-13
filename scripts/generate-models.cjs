const fs = require('fs');
const root = 'src/main/resources/assets/zhenghuocore/';
const write = (name, data) => fs.writeFileSync(root + name, JSON.stringify(data, null, 2) + '\n');
const faces = texture => Object.fromEntries(['up','down','north','south','east','west'].map(side => [side, {texture: '#' + texture}]));
const cube = (from, to, texture, rotation) => ({from, to, faces: faces(texture), ...(rotation ? {rotation} : {})});
write('models/block/starlight_generator.json', {
  parent:'block/block', textures:{particle:'minecraft:blocks/quartz_block_side', base:'minecraft:blocks/quartz_block_side', metal:'minecraft:blocks/iron_block', light:'minecraft:blocks/sea_lantern', gold:'minecraft:blocks/gold_block'},
  elements:[cube([0,0,0],[16,4,16],'metal'), cube([1,4,1],[15,6,15],'base'), cube([3,6,3],[5,12,5],'gold'), cube([11,6,3],[13,12,5],'gold'), cube([3,6,11],[5,12,13],'gold'), cube([11,6,11],[13,12,13],'gold'), cube([5,7,5],[11,13,11],'light',{origin:[8,10,8],axis:'y',angle:45,rescale:true}), cube([3,13,3],[13,15,13],'base')]
});
write('models/item/starlight_generator.json', {parent:'zhenghuocore:block/starlight_generator'});
write('blockstates/starlight_generator.json', {variants:Object.fromEntries(['up','down','north','south','east','west'].map(side => ['facing='+side,{model:'zhenghuocore:starlight_generator'}]))});
write('recipes/starlight_generator.json', {type:'minecraft:crafting_shaped',conditions:[{type:'forge:mod_loaded',modid:'astralsorcery'}],pattern:['QGQ','DND','IRI'],key:{Q:{item:'minecraft:quartz'},G:{item:'minecraft:glass'},D:{item:'minecraft:diamond'},N:{item:'minecraft:nether_star'},I:{item:'minecraft:iron_block'},R:{item:'minecraft:redstone_block'}},result:{item:'zhenghuocore:starlight_generator'}});
