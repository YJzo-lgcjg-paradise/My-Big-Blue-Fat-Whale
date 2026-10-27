# -*- coding: utf-8 -*-
import os

p = r"C:\Users\user\AppData\Local\Temp\mkdocs.py"
src = open(p, encoding="utf-8").read()

a_old = '    d.end()\n\n    return os.path.join(OUT_DIR, "3.0 项目说明.docx")'
a_new = ('    d.end()\n\n    path = os.path.join(OUT_DIR, "3.0 项目说明.docx")\n'
         '    d.save(path)\n    return path')
b_old = '    d.end()\n\n    return os.path.join(OUT_DIR, "3.0 使用手册.docx")'
b_new = ('    d.end()\n\n    path = os.path.join(OUT_DIR, "3.0 使用手册.docx")\n'
         '    d.save(path)\n    return path')

assert a_old in src, "project tail not found"
assert b_old in src, "manual tail not found"
src = src.replace(a_old, a_new, 1).replace(b_old, b_new, 1)
open(p, "w", encoding="utf-8", newline="\n").write(src)

g = {"__name__": "x"}
exec(compile(src, "m", "exec"), g)
print("A:", g["project_doc"]())
print("B:", g["manual_doc"]())
