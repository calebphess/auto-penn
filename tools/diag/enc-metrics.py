import sys,re
for line in sys.stdin:
    ts=re.search(r"\((10-[0-9]+ [0-9:.]+)\)",line).group(1)
    g=lambda k:(re.search(r"android\.media\.mediacodec\."+re.escape(k)+r"=([^,)]*)",line) or [None,None])[1]
    f=int(g("vencode.frames") or 0); l=int(g("lifetimeMs") or 0)/1000
    print(ts,g("codec"),g("width"),g("height"),f,"frames",round(l,1),"s fps=%.1f"%(f/max(l,1)))
