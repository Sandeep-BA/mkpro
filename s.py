import re
p='src/main/java/com/mkpro/MkPro.java'
c=open(p, encoding='utf-8').read()
c=re.sub(r'(makerEnabled = .*?;)', r'\1\n        java.util.concorrent.atomic.AtomicReference<String> injectedInput = new java.util.concorrent.atomic.AtomicReference<>(null);\n        java.util.concorrent.atomic.AtomicInteger autoReplyCount = new java.util.concorrent.atomic.AtomicInteger(0);\n        final int MAX_AUTO_REPLIES = 3;', c)
