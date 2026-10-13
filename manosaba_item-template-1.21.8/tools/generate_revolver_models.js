const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const outputModels = path.join(root, 'src', 'main', 'resources', 'assets', 'manosaba_item', 'models', 'item');
const outputTextures = path.join(root, 'src', 'main', 'resources', 'assets', 'manosaba_item', 'textures', 'item');
fs.mkdirSync(outputModels, { recursive: true });
fs.mkdirSync(outputTextures, { recursive: true });

const faces = ['north', 'east', 'south', 'west', 'up', 'down'];

function convert(source, modelName, textureName) {
  const model = JSON.parse(fs.readFileSync(path.join(root, source), 'utf8'));
  const texture = model.textures?.[0]?.source;
  const uvScale = (model.resolution?.width || 16) / 16;
  if (texture?.startsWith('data:image/png;base64,')) {
    fs.writeFileSync(path.join(outputTextures, `${textureName}.png`),
      Buffer.from(texture.substring('data:image/png;base64,'.length), 'base64'));
  }

  const elements = (model.elements || []).filter(element => element.type === 'cube').map(element => {
    const result = {
      from: element.from,
      to: element.to,
      faces: {}
    };
    for (const face of faces) {
      const sourceFace = element.faces?.[face];
      if (sourceFace) result.faces[face] = {
        uv: sourceFace.uv.map(value => value / uvScale),
        texture: '#0'
      };
    }
    const rotation = element.rotation || [0, 0, 0];
    const axisIndex = rotation.findIndex(value => Math.abs(value) > 0.0001);
    if (axisIndex >= 0) {
      result.rotation = {
        origin: element.origin || [0, 0, 0],
        axis: ['x', 'y', 'z'][axisIndex],
        angle: rotation[axisIndex],
        rescale: Boolean(element.rescale)
      };
    }
    return result;
  });

  const output = {
    parent: 'minecraft:item/generated',
    textures: { '0': `manosaba_item:item/${textureName}` },
    elements,
    display: {
      gui: { rotation: [0, 180, 0], scale: [0.8, 0.8, 0.8] },
      ground: { translation: [0, 2, 0], scale: [0.35, 0.35, 0.35] },
      thirdperson_righthand: { rotation: [0, 90, 0], translation: [0, 2, 0], scale: [0.45, 0.45, 0.45] },
      thirdperson_lefthand: { rotation: [0, -90, 0], translation: [0, 2, 0], scale: [0.45, 0.45, 0.45] },
      firstperson_righthand: { rotation: [0, 90, 0], translation: [0, 2, 0], scale: [0.55, 0.55, 0.55] },
      firstperson_lefthand: { rotation: [0, -90, 0], translation: [0, 2, 0], scale: [0.55, 0.55, 0.55] }
    }
  };
  fs.writeFileSync(path.join(outputModels, `${modelName}.json`), JSON.stringify(output));
}

convert('左轮.bbmodel', 'revolver_3d', 'revolver_3d');
convert('子弹.bbmodel', 'revolver_bullet_3d', 'revolver_bullet_3d');
convert('蛋壳.bbmodel', 'shell_casing_3d', 'shell_casing_3d');
