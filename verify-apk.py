"""Check module entry, fixed scope, API references and compile-only packaging."""
import hashlib
import json
import pathlib
import struct
import sys
import zipfile

path = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(path) as apk:
    assert len(apk.namelist()) == len(set(apk.namelist())), 'Duplicate archive entry'
    assert 'assets/xposed_init' not in apk.namelist(), 'Legacy entry present'
    assert apk.read('META-INF/xposed/java_init.list').decode().strip() == 'nfc.wallet.compat.WalletModule'
    assert set(apk.read('META-INF/xposed/scope.list').decode().splitlines()) == {
        'com.finshell.wallet', 'com.android.nfc', 'com.android.se'}
    props = dict(line.split('=', 1) for line in apk.read('META-INF/xposed/module.prop').decode().splitlines())
    assert props == dict(minApiVersion='102', targetApiVersion='102', staticScope='true',
                         exceptionMode='protective', autoHotReload='false')
    assert [n for n in apk.namelist() if n.endswith('.dex')] == ['classes.dex']
    dex = apk.read('classes.dex')
    assert b'de/robv/android/xposed' not in dex, 'Legacy API reference'
    assert b'io/github/knox/walletalphacompat' not in dex, 'Old namespace reference'
    assert b'io/github/libxposed/api/XposedModule' in dex
    assert b'setId\0' in dex and b'onPackageReady\0' in dex
    def u32(offset): return struct.unpack_from('<I', dex, offset)[0]
    assert dex[:4] == b'dex\n' and u32(32) == len(dex)
    strings = []
    for i in range(u32(56)):
        pos = u32(u32(60) + i * 4)
        while dex[pos] & 0x80: pos += 1
        pos += 1
        strings.append(dex[pos:dex.index(b'\0', pos)].decode('utf-8', errors='replace'))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    classes = [types[u32(u32(100) + i * 32)] for i in range(u32(96))]
    assert classes and all(c.startswith('Lnfc/wallet/compat/') for c in classes), 'Bundled external runtime classes'
    assert 'Lnfc/wallet/compat/WalletModule;' in classes
print(json.dumps(dict(package='nfc.wallet.compat', api=102, checks='PASS',
                      sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                      dexSha256=hashlib.sha256(dex).hexdigest(), definedClasses=len(classes)), indent=2))
