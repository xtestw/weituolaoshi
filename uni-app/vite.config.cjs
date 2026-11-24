const { defineConfig } = require('vite')
const uni = require('@dcloudio/vite-plugin-uni').default
const fs = require('node:fs')
const path = require('node:path')

// The dev compiler may emit touristappid, which cannot exchange a real wx.login code.
const useConfiguredWeChatAppId = {
  name: 'use-configured-wechat-appid',
  closeBundle() {
    const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, 'src/manifest.json'), 'utf8'))
    const configPath = path.join(__dirname, 'dist/dev/mp-weixin/project.config.json')
    if (!fs.existsSync(configPath)) return
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'))
    const appId = manifest['mp-weixin'] && manifest['mp-weixin'].appid
    let changed = false
    if (appId && config.appid !== appId) {
      config.appid = appId
      changed = true
    }
    // Uni-app's generated entry must stay available to the WeChat runtime.
    config.setting = config.setting || {}
    if (config.setting.ignoreDevUnusedFiles !== false) {
      config.setting.ignoreDevUnusedFiles = false
      changed = true
    }
    if (changed) {
      fs.writeFileSync(configPath, JSON.stringify(config, null, 2) + '\n')
    }
  }
}

module.exports = defineConfig({ plugins: [uni(), useConfiguredWeChatAppId] })
