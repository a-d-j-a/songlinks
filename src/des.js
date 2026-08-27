const CryptoJS = require('crypto-js');

const DES_KEY = '38346591';

function decryptDesEcb(encryptedBase64, key = DES_KEY) {
  const keyBytes = CryptoJS.enc.Utf8.parse(key);
  const encryptedWords = CryptoJS.enc.Base64.parse(encryptedBase64);

  const decrypted = CryptoJS.DES.decrypt(
    { ciphertext: encryptedWords },
    keyBytes,
    {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7
    }
  );

  return decrypted.toString(CryptoJS.enc.Utf8);
}

module.exports = { decryptDesEcb, DES_KEY };
