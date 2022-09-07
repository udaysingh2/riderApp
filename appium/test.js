const wdio = require('webdriverio');
const assert = require('assert');
const find = require('appium-flutter-finder');

const osSpecificOps = process.env.APPIUM_OS === 'android' ? {
  platformName: 'Android',
  deviceName: 'emulator-5554',
  // @todo support non-unix style path
  app: __dirname +  '/../build/app/outputs/apk/debug/app-debug.apk',
}: process.env.APPIUM_OS === 'ios' ? {
  platformName: 'iOS',
  platformVersion: '13.2',
  deviceName: 'iPhone X',
  noReset: true,
  app: __dirname +  '/../ios/psrider.zip',

} : {};

const opts = {
  port: 4723,
  capabilities: {
    ...osSpecificOps,
    automationName: 'Flutter'
  }
};

(async () => {
  console.log('Initial app testing')

    const driver = await wdio.remote(opts);
    const counterTextFinder = find.byValueKey('languageText');
    await driver.switchContext('NATIVE_APP');
    //await driver.saveScreenShot('./native.screenshot.png')
    await driver.switchContext('FLUTTER');

    assert.strictEqual(await driver.getElementText(counterTextFinder), 'SCB Rider App Home View');

    driver.deleteSession();
})();