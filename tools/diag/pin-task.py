# usage: pin.py <pkg> <seconds> ; keeps <pkg>'s tasks on the AutoPennMirror display
import subprocess,re,sys,time
pkg=sys.argv[1]; end=time.time()+float(sys.argv[2])
def sh(c): return subprocess.run(["adb","-s","ZY22LBXXFN","shell",c],capture_output=True,text=True).stdout
while time.time()<end:
    disp=re.findall(r'DisplayInfo\{"AutoPennMirror".*?displayId (\d+)',sh("dumpsys display"))
    if disp:
        d=disp[-1]; cur=None
        for line in sh("dumpsys activity activities").splitlines():
            m=re.match(r"Display #(\d+)",line)
            if m: cur=m.group(1)
            t=re.search(r"Task\{\w+ #(\d+) type=standard A=\d+:"+re.escape(pkg),line)
            if t and cur!=d:
                print("move task",t.group(1),"from display",cur,"to",d,flush=True)
                sh(f"am display move-stack {t.group(1)} {d}")
    time.sleep(1.5)
