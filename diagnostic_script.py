import subprocess
import os

def run_command(cmd, cwd='F:\\Context'):
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, shell=True)
    return result.stdout, result.stderr, result.returncode

os.chdir('F:\\Context')

print('=' * 80)
print('1. Current branch')
print('=' * 80)
stdout, stderr, code = run_command('git rev-parse --abbrev-ref HEAD')
print(f'Exit code: {code}')
print(f'Stdout: {repr(stdout)}')
print(f'Stderr: {repr(stderr)}')

print('\n' + '=' * 80)
print('2. Concise status')
print('=' * 80)
stdout, stderr, code = run_command('git status --short --branch')
print(f'Exit code: {code}')
print(f'Stdout: {repr(stdout)}')
print(f'Stderr: {repr(stderr)}')

print('\n' + '=' * 80)
print('3. Check-ignore for .worktrees')
print('=' * 80)
stdout, stderr, code = run_command('git check-ignore -v .worktrees')
print(f'Exit code: {code}')
print(f'Stdout: {repr(stdout)}')
print(f'Stderr: {repr(stderr)}')

print('\n' + '=' * 80)
print('4. Local branch feature/reporting-stabilization')
print('=' * 80)
stdout, stderr, code = run_command('git branch --list feature/reporting-stabilization')
print(f'Exit code: {code}')
print(f'Stdout: {repr(stdout)}')
print(f'Stderr: {repr(stderr)}')

print('\n' + '=' * 80)
print('5. Remote branch */feature/reporting-stabilization')
print('=' * 80)
stdout, stderr, code = run_command('git branch -r --list */feature/reporting-stabilization')
print(f'Exit code: {code}')
print(f'Stdout: {repr(stdout)}')
print(f'Stderr: {repr(stderr)}')

print('\n' + '=' * 80)
print('6. Directory exists check')
print('=' * 80)
dir_path = r'F:\Context\.worktrees\feature-reporting-stabilization'
exists = os.path.exists(dir_path)
is_dir = os.path.isdir(dir_path)
print(f'Path: {dir_path}')
print(f'os.path.exists(): {exists}')
print(f'os.path.isdir(): {is_dir}')

print('\n' + '=' * 80)
print('7. Worktree list')
print('=' * 80)
stdout, stderr, code = run_command('git worktree list')
print(f'Exit code: {code}')
print(f'Stdout: {repr(stdout)}')
print(f'Stderr: {repr(stderr)}')
