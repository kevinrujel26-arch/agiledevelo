// HU-08: subida de hasta 5 fotos JPG o PNG
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const multer = require('multer');
const config = require('../config');
const { errores } = require('../utils/errores');

const DIR_MAQUINAS = path.join(config.dirSubidas, 'maquinas');
fs.mkdirSync(DIR_MAQUINAS, { recursive: true });

const TIPOS = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
};

const almacenamiento = multer.diskStorage({
  destination: (_req, _archivo, cb) => cb(null, DIR_MAQUINAS),
  filename: (_req, archivo, cb) => cb(null, `${crypto.randomUUID()}${TIPOS[archivo.mimetype]}`),
});

const subirFotos = multer({
  storage: almacenamiento,
  limits: { fileSize: config.fotos.maxBytes, files: config.fotos.maxPorMaquina },
  fileFilter: (_req, archivo, cb) => {
    if (!TIPOS[archivo.mimetype]) {
      return cb(errores.solicitudInvalida('Solo se permiten fotos en formato JPG o PNG'));
    }
    cb(null, true);
  },
}).array('fotos', config.fotos.maxPorMaquina);

/**
 * El tipo que declara el navegador se puede falsificar, así que además
 * revisamos los primeros bytes del archivo ("firma mágica").
 */
async function esImagenReal(rutaArchivo, tipoMime) {
  const manejador = await fs.promises.open(rutaArchivo, 'r');
  try {
    const buffer = Buffer.alloc(8);
    await manejador.read(buffer, 0, 8, 0);
    if (tipoMime === 'image/jpeg') return buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
    if (tipoMime === 'image/png') return buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    return false;
  } finally {
    await manejador.close();
  }
}

/** Ruta pública con la que se sirve la foto (ver app.js: /uploads) */
function rutaPublica(nombreArchivo) {
  return `/uploads/maquinas/${nombreArchivo}`;
}

/** Borra del disco una foto a partir de su ruta pública. No falla si ya no existe. */
async function borrarArchivoFoto(ruta) {
  const archivo = path.join(DIR_MAQUINAS, path.basename(ruta));
  await fs.promises.unlink(archivo).catch(() => {});
}

async function borrarArchivosSubidos(archivos = []) {
  await Promise.all(archivos.map((a) => fs.promises.unlink(a.path).catch(() => {})));
}

module.exports = { subirFotos, esImagenReal, rutaPublica, borrarArchivoFoto, borrarArchivosSubidos };
