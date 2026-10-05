"""Local test harness. Test credentials and artifacts are temporary and never packaged as data."""
from __future__ import annotations
import json, os, socket, subprocess, tempfile, time, urllib.request, urllib.error
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
PASSWORD = 'test-only-password-947521'
JAR = Path(os.getenv('GR_TEST_JAR', str(ROOT/'repository-server/target/graph-repository.jar'))).resolve()

def port() -> int:
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]

class Server:
    def __init__(self, home: Path, **extra: str):
        self.home = home
        self.port = port()
        self.url = f'http://127.0.0.1:{self.port}'
        java_home = os.getenv('JAVA_HOME')
        self.java = str(Path(java_home) / 'bin/java') if java_home else 'java'
        self.env = {**os.environ, 'GR_HOME':str(home), 'GR_PORT':str(self.port),
                    'GR_BIND':'127.0.0.1', 'GR_PUBLIC_URL':self.url,
                    'GR_BOOTSTRAP_PASSWORD':PASSWORD, **extra}
        self.process = None
        self.log = None
    def start(self):
        if not JAR.is_file():
            raise FileNotFoundError(f"Build the real Spring Boot JAR first: {JAR}")
        self.home.mkdir(parents=True, exist_ok=True)
        self.log = (self.home.parent / ('server-'+str(self.port)+'.log')).open('a')
        self.process = subprocess.Popen([self.java, '-jar', str(JAR)],
            cwd=ROOT, env=self.env, stdout=self.log, stderr=self.log)
        for _ in range(900):
            if self.process.poll() is not None:
                self.log.flush()
                raise RuntimeError(f'Server exited: {(self.home.parent / ("server-"+str(self.port)+".log")).read_text()}')
            try:
                with urllib.request.urlopen(self.url+'/healthz', timeout=.3) as response:
                    if response.status == 200: return self
            except (OSError, urllib.error.URLError): time.sleep(.1)
        self.stop()
        raise TimeoutError('Spring Boot startup timed out; inspect the test server log')
    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try: self.process.wait(timeout=40)
            except subprocess.TimeoutExpired: self.process.kill(); self.process.wait()
        if self.log: self.log.close()
    def command(self, *args: str):
        return subprocess.run([self.java,'-jar',str(JAR),*args],
            env=self.env,cwd=ROOT,text=True,capture_output=True,timeout=60)
    def __enter__(self): return self.start()
    def __exit__(self,*exc): self.stop()
