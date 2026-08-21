/*************************************************
 * H JOEL POD BACKEND - CLEAN NEW
 *************************************************/
const CONFIG = {
  SPREADSHEET_ID: '15k3HuWZBPbdx29A2MExnVLFKXom3LLFPxVpmQzk6cxA',
  SHEET_POD: 'POD',
  SHEET_VALIDATION: 'Validation',
  POD_FOLDER_ID: '142-0g7NgCa_oNhOYOD57oic6osz0IrJA'
};

function doGet(e) {
  const action = String((e && e.parameter && e.parameter.action) || '').trim();

  if (action === 'getAreas') {
    return json_({
      success: true,
      areas: getAreaList_()
    });
  }

  return json_({
    success: true,
    message: 'H JOEL POD API aktif.'
  });
}

function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents || '{}');

    if (!data.tanggalPOD) throw new Error('Tanggal POD belum diisi.');
    if (!data.awb) throw new Error('AWB belum diisi.');
    if (!data.area) throw new Error('Area belum dipilih.');
    if (!data.foto1 || !data.foto2 || !data.foto3 || !data.foto4) {
      throw new Error('Semua 4 foto wajib diisi.');
    }

    const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
    const sh = ss.getSheetByName(CONFIG.SHEET_POD);

    if (!sh) throw new Error('Sheet POD tidak ditemukan.');

    const lastRow = sh.getLastRow();

    if (lastRow >= 2) {
      const awbs = sh
        .getRange(2, 3, lastRow - 1, 1)
        .getDisplayValues()
        .flat()
        .map(v => String(v).trim().toUpperCase());

      if (awbs.includes(String(data.awb).trim().toUpperCase())) {
        throw new Error('AWB ' + data.awb + ' sudah pernah disimpan.');
      }
    }

    const root = DriveApp.getFolderById(CONFIG.POD_FOLDER_ID);
    const awbFolder = getOrCreateSubFolder_(root, clean_(data.awb));

    const foto1 = saveImage_(awbFolder, data.foto1, 'UNIT_PENERIMA_' + clean_(data.awb));
    const foto2 = saveImage_(awbFolder, data.foto2, 'PLANG_SEKOLAH_' + clean_(data.awb));
    const foto3 = saveImage_(awbFolder, data.foto3, 'BAST_' + clean_(data.awb));
    const foto4 = saveImage_(awbFolder, data.foto4, 'SERIAL_NUMBER_' + clean_(data.awb));

    sh.appendRow([
      new Date(data.tanggalPOD + 'T00:00:00'),
      new Date(),
      String(data.awb).trim(),
      String(data.area).trim(),
      foto1,
      foto2,
      foto3,
      foto4
    ]);

    const row = sh.getLastRow();
    sh.getRange(row, 1).setNumberFormat('dd/MM/yyyy');
    sh.getRange(row, 2).setNumberFormat('dd/MM/yyyy HH:mm:ss');

    return json_({
      success: true,
      message: 'POD berhasil disimpan.',
      awb: String(data.awb).trim(),
      row: row
    });

  } catch (err) {
    return json_({
      success: false,
      message: err.message || String(err)
    });
  }
}

function getAreaList_() {
  const ss = SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
  const sh = ss.getSheetByName(CONFIG.SHEET_VALIDATION);

  if (!sh) throw new Error('Sheet Validation tidak ditemukan.');

  const lastRow = sh.getLastRow();

  if (lastRow < 2) return [];

  return [...new Set(
    sh.getRange(2, 1, lastRow - 1, 1)
      .getDisplayValues()
      .flat()
      .map(v => String(v).trim())
      .filter(Boolean)
  )];
}

function getOrCreateSubFolder_(parent, name) {
  const found = parent.getFoldersByName(name);
  return found.hasNext() ? found.next() : parent.createFolder(name);
}

function saveImage_(folder, dataUrl, prefix) {
  const match = String(dataUrl).match(/^data:(image\/[^;]+);base64,(.+)$/);

  if (!match) throw new Error('Format foto tidak valid.');

  const mime = match[1];
  const bytes = Utilities.base64Decode(match[2]);

  let ext = 'jpg';
  if (mime.includes('png')) ext = 'png';
  if (mime.includes('webp')) ext = 'webp';

  const stamp = Utilities.formatDate(
    new Date(),
    Session.getScriptTimeZone(),
    'yyyyMMdd_HHmmss'
  );

  const file = folder.createFile(
    Utilities.newBlob(
      bytes,
      mime,
      prefix + '_' + stamp + '.' + ext
    )
  );

  return file.getUrl();
}

function clean_(text) {
  return String(text || '')
    .replace(/[^\w\-]/g, '_')
    .substring(0, 80);
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
