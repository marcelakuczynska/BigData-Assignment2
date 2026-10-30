from pathlib import Path
import csv

folder = Path(__file__).resolve().parent
keys = ['mode', 'method', 'n', 'sparsity_pct', 'repetition']
with (folder / 'raw_times.csv').open(newline='', encoding='utf-8') as f:
    times = list(csv.DictReader(f))
with (folder / 'raw_memory.csv').open(newline='', encoding='utf-8') as f:
    memory = {tuple(row[k] for k in keys): row for row in csv.DictReader(f)}

fields = ['mode', 'method', 'n', 'sparsity_pct', 'target_density_pct',
          'actual_density_a_pct', 'actual_density_b_pct', 'numeric_type',
          'algorithm_worker_threads', 'java_version', 'repetition', 'time_ms',
          'observed_heap_delta_mib', 'nnz_a', 'nnz_b', 'nnz_c', 'output_representation']
with (folder / 'measurements_with_context.csv').open('w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    for r in times:
        m = memory[tuple(r[k] for k in keys)]
        n = int(r['n'])
        writer.writerow({'mode': r['mode'], 'method': r['method'], 'n': n,
            'sparsity_pct': r['sparsity_pct'],
            'target_density_pct': r['density_pct'],
            'actual_density_a_pct': f"{100*int(r['nnz_a'])/n**2:.6f}",
            'actual_density_b_pct': f"{100*int(r['nnz_b'])/n**2:.6f}",
            'numeric_type': 'Java long (signed 64-bit integer)',
            'algorithm_worker_threads': 1, 'java_version': '21',
            'repetition': r['repetition'], 'time_ms': r['time_ms'],
            'observed_heap_delta_mib': m['observed_heap_delta_mib'],
            'nnz_a': r['nnz_a'], 'nnz_b': r['nnz_b'], 'nnz_c': r['nnz_c'],
            'output_representation': 'CSR' if r['method'] == 'CSR' else 'long[][]'})
print('Saved measurements_with_context.csv')
