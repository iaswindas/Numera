import subprocess
import os

def run_command(cmd, cwd='F:\\Context'):
    try:
        result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, shell=True)
        return result.stdout, result.stderr, result.returncode
    except Exception as e:
        return '', str(e), -1

os.chdir('F:\\Context')

print('=' * 80)
print('1. Current branch')
print('=' * 80)
stdout, stderr, code = run_command('git rev-parse --abbrev-ref HEAD')
print('Command: git rev-parse --abbrev-ref HEAD')
print('Exit code: {}'.format(code))
print('Stdout:\n{}'.format(stdout if stdout else '(empty)'))
if stderr:
    print('Stderr:\n{}'.format(stderr))

print('\n' + '=' * 80)
print('2. Concise status')
print('=' * 80)
stdout, stderr, code = run_command('git status --short --branch')
print('Command: git status --short --branch')
print('Exit code: {}'.format(code))
print('Stdout:\n{}'.format(stdout if stdout else '(empty)'))
if stderr:
    print('Stderr:\n{}'.format(stderr))

print('\n' + '=' * 80)
print('3. Check-ignore for .worktrees')
print('=' * 80)
stdout, stderr, code = run_command('git check-ignore -v .worktrees')
print('Command: git check-ignore -v .worktrees')
print('Exit code: {}'.format(code))
print('Stdout:\n{}'.format(stdout if stdout else '(empty)'))
print('Stderr:\n{}'.format(stderr if stderr else '(empty)'))

print('\n' + '=' * 80)
print('4. Local branch exists: feature/reporting-stabilization')
print('=' * 80)
stdout, stderr, code = run_command('git branch --list feature/reporting-stabilization')
print('Command: git branch --list feature/reporting-stabilization')
print('Exit code: {}'.format(code))
print('Stdout:\n{}'.format(stdout if stdout else '(empty)'))
if stderr:
    print('Stderr:\n{}'.format(stderr))

print('\n' + '=' * 80)
print('5. Remote branch exists: */feature/reporting-stabilization')
print('=' * 80)
stdout, stderr, code = run_command('git branch -r --list */feature/reporting-stabilization')
print('Command: git branch -r --list */feature/reporting-stabilization')
print('Exit code: {}'.format(code))
print('Stdout:\n{}'.format(stdout if stdout else '(empty)'))
if stderr:
    print('Stderr:\n{}'.format(stderr))

print('\n' + '=' * 80)
print('6. Directory existence check')
print('=' * 80)
dir_path = r'F:\Context\.worktrees\feature-reporting-stabilization'
exists = os.path.exists(dir_path)
is_dir = os.path.isdir(dir_path)
print('Path: {}'.format(dir_path))
print('os.path.exists(): {}'.format(exists))
print('os.path.isdir(): {}'.format(is_dir))

print('\n' + '=' * 80)
print('7. Worktree list')
print('=' * 80)
stdout, stderr, code = run_command('git worktree list')
print('Command: git worktree list')
print('Exit code: {}'.format(code))
print('Stdout:\n{}'.format(stdout if stdout else '(empty)'))
if stderr:
    print('Stderr:\n{}'.format(stderr))
