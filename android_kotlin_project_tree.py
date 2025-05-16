import os
import re
import sys

OUTPUT_FILE = 'project_structure.txt'

# Регулярные выражения для Kotlin
CLASS_PATTERN = re.compile(r'^\s*(class|data class|object)\s+(\w+)', re.MULTILINE)
FUN_PATTERN = re.compile(r'^\s*(?:suspend\s+)?fun\s+(\w+)', re.MULTILINE)
PROP_PATTERN = re.compile(r'^\s*(val|var)\s+(\w+)', re.MULTILINE)

def contains_target_files(path):
    """Проверяет, есть ли в папке (или её подпапках) .kt или .xml файлы."""
    for root, dirs, files in os.walk(path):
        for f in files:
            if f.endswith('.kt') or f.endswith('.xml'):
                return True
    return False

def analyze_kotlin_file(file_path, prefix, output_lines):
    print(f"📄 Анализ файла: {file_path}")
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        inside_class = False
        current_class_indent = None
        class_name = None
        class_buffer = []

        def flush_class():
            if class_name:
                output_lines.append(prefix + '├── ' + f'class {class_name}')
                class_prefix = prefix + '│   '
                for line in class_buffer:
                    if match := FUN_PATTERN.match(line):
                        output_lines.append(class_prefix + '├── ' + f'{match.group(1)}()')
                    elif match := PROP_PATTERN.match(line):
                        output_lines.append(class_prefix + '├── ' + f'{match.group(1)} {match.group(2)}')

        for line in lines:
            if CLASS_PATTERN.match(line):
                flush_class()
                class_name = CLASS_PATTERN.match(line).group(2)
                current_class_indent = len(line) - len(line.lstrip())
                inside_class = True
                class_buffer = []
                continue

            if inside_class:
                indent = len(line) - len(line.lstrip())
                if indent <= current_class_indent and not line.strip().startswith("//"):
                    flush_class()
                    inside_class = False
                    class_name = None
                    class_buffer = []
                else:
                    class_buffer.append(line)
            else:
                if match := FUN_PATTERN.match(line):
                    output_lines.append(prefix + '├── ' + f'{match.group(1)}()')
                elif match := PROP_PATTERN.match(line):
                    output_lines.append(prefix + '├── ' + f'{match.group(1)} {match.group(2)}')

        flush_class()

    except Exception as e:
        output_lines.append(prefix + '└── <error reading file>')

def list_project_tree(root_dir, rel_path='', prefix='', output_lines=None):
    if output_lines is None:
        output_lines = []

    abs_path = os.path.join(root_dir, rel_path)
    if not contains_target_files(abs_path):
        return output_lines  # Пропустить пустую папку

    try:
        entries = sorted(os.listdir(abs_path))
    except PermissionError:
        return output_lines

    files_and_dirs = []
    for entry in entries:
        full_path = os.path.join(abs_path, entry)
        if os.path.isdir(full_path) and contains_target_files(full_path):
            files_and_dirs.append((entry, 'dir'))
        elif entry.endswith('.kt') or entry.endswith('.xml'):
            files_and_dirs.append((entry, 'file'))

    for i, (entry, entry_type) in enumerate(files_and_dirs):
        connector = '└── ' if i == len(files_and_dirs) - 1 else '├── '
        full_rel_path = os.path.join(rel_path, entry)

        output_lines.append(prefix + connector + entry)
        new_prefix = prefix + ('    ' if i == len(files_and_dirs) - 1 else '│   ')

        if entry_type == 'dir':
            print(f"📂 Переход в папку: {full_rel_path}")
            list_project_tree(root_dir, full_rel_path, new_prefix, output_lines)
        elif entry.endswith('.kt'):
            analyze_kotlin_file(os.path.join(root_dir, full_rel_path), new_prefix, output_lines)

    return output_lines

if __name__ == '__main__':
    root_dir = os.getcwd()
    print(f"🚀 Запуск анализа проекта: {os.path.basename(root_dir)}\n")
    output_lines = [os.path.basename(root_dir) + '/']
    list_project_tree(root_dir, '', '', output_lines)

    print("\n💾 Сохранение в файл...")
    with open(os.path.join(root_dir, OUTPUT_FILE), 'w', encoding='utf-8') as f:
        f.write('\n'.join(output_lines))

    print(f"✅ Готово! Структура проекта сохранена в: {OUTPUT_FILE}")
