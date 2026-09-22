// Se ejecuta antes de cada archivo de pruebas
const os = require('os');
const path = require('path');

process.env.NODE_ENV = 'test';
process.env.UPLOAD_DIR = path.join(os.tmpdir(), 'alquiler-maquinaria-pruebas');
