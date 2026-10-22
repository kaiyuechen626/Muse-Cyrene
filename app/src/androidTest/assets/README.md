# LAN QR decoder fixture

`lan-pair.gif` and `lan-pair.txt` are a synthetic interoperability fixture generated
with the desktop project's vendored qrcode-generator (license retained there).
The UUIDs are fixed repeated digits and the key is 32 bytes of 0x07. It is public
test data, unrelated to any production pairing or device credential. The encoded
address is only a sample; this fixture tests decoding, not a live connection.

The network instrumentation test instead receives a fresh URI for an isolated,
disposable desktop service. Never check a live pairing URI into this directory.
