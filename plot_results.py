from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
DATA = pd.read_csv(ROOT / 'summary.csv')

plt.rcParams.update({'font.size': 10, 'axes.spines.top': False, 'axes.spines.right': False, 'figure.dpi': 140})

# A: all dense methods on the same inputs
part_a = DATA[DATA['mode'] == 'dense']
fig, ax = plt.subplots(figsize=(7.3, 3.7))
for name in ['Baseline', 'Reordered', 'Blocked']:
    d = part_a[part_a.method == name].sort_values('n')
    ax.plot(d.n, d.median_ms, marker='o', linewidth=2, label=name)
ax.set_yscale('log')
ax.set_xlabel('Matrix size n (n × n)')
ax.set_ylabel('Median time (ms, log scale)')
ax.set_title('Dense multiplication — timing by matrix size')
ax.grid(alpha=.22, which='both')
ax.legend(frameon=False)
fig.tight_layout(); fig.savefig(ROOT / 'fig1_dense_time.png', dpi=210); plt.close(fig)

# B: medium-sized to large sparse comparison, selected n=320
subset = DATA[(DATA['mode'] == 'sparse_compare') & (DATA.n == 320)]
fig, ax = plt.subplots(figsize=(7.3, 3.65))
for name in ['Reordered', 'CSR']:
    d = subset[subset.method == name].sort_values('sparsity_pct')
    ax.plot(d.sparsity_pct, d.median_ms, marker='o', linewidth=2, label=name)
ax.set_yscale('log')
ax.set_xticks([50, 80, 95, 99]); ax.set_xticklabels(['50%', '80%', '95%', '99%'])
ax.set_xlabel('Zero entries in each input (target %)')
ax.set_ylabel('Median time (ms, log scale)')
ax.set_title('Sparse test at n = 320 — multiplication time')
ax.grid(alpha=.22, which='both')
ax.legend(frameon=False)
fig.tight_layout(); fig.savefig(ROOT / 'fig2_sparse_time.png', dpi=210); plt.close(fig)

# C: speedup over reordered dense for n and target percentage zeros
p = DATA[DATA['mode'] == 'sparse_compare'].pivot(index=['n','sparsity_pct'], columns='method',values='median_ms')
p['speedup'] = p['Reordered'] / p['CSR']
ns = [80, 160, 240, 320]; ss = [50,80,95,99]
vals = np.array([[p.loc[(n,sp),'speedup'] for sp in ss] for n in ns])
fig, ax = plt.subplots(figsize=(7.3, 3.6))
img = ax.imshow(np.log10(vals), aspect='auto', cmap='Blues')
ax.set_xticks(range(4), [f'{a}%' for a in ss]); ax.set_yticks(range(4), [str(n) for n in ns])
ax.set_xlabel('Target zero entries in inputs'); ax.set_ylabel('Matrix size n')
ax.set_title('CSR speedup relative to reordered dense')
for i in range(4):
    for j in range(4):
        ax.text(j,i, f'{vals[i,j]:.1f}×', ha='center', va='center', color='white' if np.log10(vals[i,j]) > 1.2 else '#20262d',fontsize=9)
fig.tight_layout(); fig.savefig(ROOT/'fig3_sparse_speedup.png',dpi=210); plt.close(fig)

# D: storage of A, B, C (numeric array payload estimate; not full heap)
fig, ax = plt.subplots(figsize=(7.3, 3.6))
ind = np.arange(4); w=.34
for idx,name in enumerate(['Reordered','CSR']):
    d=subset[subset.method==name].set_index('sparsity_pct').loc[ss]
    ax.bar(ind + (-w/2 if idx==0 else w/2),d.estimated_payload_mib,width=w,label=name)
ax.set_xticks(ind,[f'{a}%' for a in ss]); ax.set_xlabel('Target zero entries in inputs')
ax.set_ylabel('Estimated numeric array payload (MiB)')
ax.set_title('Estimated storage of A, B and C at n = 320')
ax.grid(axis='y',alpha=.22);ax.legend(frameon=False)
fig.tight_layout();fig.savefig(ROOT/'fig4_storage_estimate.png',dpi=210);plt.close(fig)

print('Four figures saved in', ROOT)
