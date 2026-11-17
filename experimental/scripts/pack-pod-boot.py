#!/usr/bin/env python3
"""Pack a Pod boot image the same way stock is packed (v0 header, page 4096, appended DTB),
and sign it the same way the recovery kit signs images (public AOSP test verity key).

usage (python3 -I): pack-pod-boot.py <Image.gz> <dtb> <stock_boot.img> <ramdisk.gz> <out_unsigned.img> <out_signed.img>

Host-side only. Nothing here talks to the Pod.
"""
import hashlib, struct, sys
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding

KEY = __import__('os').environ['VERITY_KEY']  # AOSP public test verity key (build/target/product/security/verity.pk8 pair)

imgz, dtb, stock_p, rd_p, out_u, out_s = sys.argv[1:7]
stock = open(stock_p, 'rb').read()
assert stock[:8] == b'ANDROID!'
(ksz, kaddr, rsz, raddr, ssz, saddr, taddr, psz, dtsz, osver) = struct.unpack_from('<10I', stock, 8)
name = stock[48:64]; cmdline = stock[64:576]; extra = stock[608:1632]
import os
xc = os.environ.get('POD_EXTRA_CMDLINE', '').encode()
if xc:
    extra = (xc + b'\0').ljust(1024, b'\0')
assert psz == 4096 and dtsz == 0 and ssz == 0

kernel = open(imgz, 'rb').read() + open(dtb, 'rb').read()   # Image.gz-dtb layout
ramdisk = open(rd_p, 'rb').read()
pad = lambda b: b + b'\0' * (-len(b) % psz)

sha = hashlib.sha1(); sha.update(kernel); sha.update(struct.pack('<I', len(kernel)))
sha.update(ramdisk); sha.update(struct.pack('<I', len(ramdisk))); sha.update(struct.pack('<I', 0)); sha.update(struct.pack('<I', 0))
idf = sha.digest().ljust(32, b'\0')

hdr = b'ANDROID!' + struct.pack('<10I', len(kernel), kaddr, len(ramdisk), raddr, 0, saddr, taddr, psz, 0, osver) + name + cmdline + idf + extra
body = pad(hdr) + pad(kernel) + pad(ramdisk)
open(out_u, 'wb').write(body)
print('unsigned %s: %d bytes (kernel %d, ramdisk %d)' % (out_u, len(body), len(kernel), len(ramdisk)))

# --- sign like sign_boot.py: footer cert taken from the stock image's own footer
foot_off = 4096 + len(pad(stock[4096:4096 + ksz])) + len(pad(stock[4096 + len(pad(stock[4096:4096 + ksz])):][:rsz]))
cert = stock[foot_off:foot_off + 1045]
assert cert[:2] == b'\x30\x82', 'stock footer cert not found at 0x%x' % foot_off
key = serialization.load_pem_private_key(open(KEY, 'rb').read(), password=None)
S = (len(body) + 4095) // 4096 * 4096
aa_body = b'\x13\x05/boot' + b'\x02\x04' + struct.pack('>I', S)
aa = b'\x30' + bytes([len(aa_body)]) + aa_body
padded = body + b'\0' * (S - len(body))
sig = key.sign(padded + aa, padding.PKCS1v15(), hashes.SHA256())
signed = padded + cert + aa + b'\x04\x82\x01\x00' + sig
open(out_s, 'wb').write(signed)
print('signed   %s: %d bytes (padded %d + %d footer)' % (out_s, len(signed), S, len(signed) - S))
