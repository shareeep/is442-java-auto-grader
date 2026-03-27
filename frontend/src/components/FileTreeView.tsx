import React, { useState } from 'react';
import { ChevronRight, ChevronDown, FileCode2, FileText, FileSpreadsheet, Folder, FolderOpen } from 'lucide-react';

export interface FileNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  children: FileNode[];
  file?: File;
  zipEntry?: { zipFile: File; entryPath: string };
}

export function buildFileTree(files: File[], rootName: string): FileNode {
  const root: FileNode = { name: rootName, path: rootName, type: 'directory', children: [] };

  for (const file of files) {
    const rel = (file as any).webkitRelativePath as string | undefined;
    const parts = rel ? rel.split('/') : [file.name];
    let cur = root;
    const start = parts.length > 1 ? 1 : 0;

    for (let i = start; i < parts.length; i++) {
      const part = parts[i];
      const isLeaf = i === parts.length - 1;
      if (isLeaf) {
        cur.children.push({ name: part, path: parts.slice(0, i + 1).join('/'), type: 'file', children: [], file });
      } else {
        let child = cur.children.find(c => c.name === part && c.type === 'directory');
        if (!child) {
          child = { name: part, path: parts.slice(0, i + 1).join('/'), type: 'directory', children: [] };
          cur.children.push(child);
        }
        cur = child;
      }
    }
  }

  const sort = (node: FileNode) => {
    node.children.sort((a, b) => {
      if (a.type !== b.type) return a.type === 'directory' ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    node.children.forEach(sort);
  };
  sort(root);
  return root;
}

function fileIcon(name: string) {
  const ext = name.split('.').pop()?.toLowerCase();
  if (ext === 'java') return <FileCode2 size={13} className="text-vsc-orange shrink-0" />;
  if (ext === 'csv') return <FileSpreadsheet size={13} className="text-vsc-green shrink-0" />;
  if (ext === 'pdf') return <FileText size={13} className="text-vsc-blue shrink-0" />;
  return <FileText size={13} className="text-vsc-text-dim shrink-0" />;
}

interface NodeProps {
  node: FileNode;
  depth: number;
  selected: string | null;
  onSelect: (node: FileNode) => void;
}

const TreeNode: React.FC<NodeProps> = ({ node, depth, selected, onSelect }) => {
  const [open, setOpen] = useState(depth < 2);
  const isSelected = selected === node.path;

  if (node.type === 'directory') {
    return (
      <div>
        <button
          onClick={() => setOpen(o => !o)}
          className={`flex items-center gap-1.5 w-full text-left px-2 py-0.5 rounded text-xs font-medium transition-colors ${
            open ? 'text-vsc-text' : 'text-vsc-text-dim hover:text-vsc-text'
          } hover:bg-white/5`}
          style={{ paddingLeft: `${8 + depth * 14}px` }}
        >
          {open
            ? <><ChevronDown size={12} className="shrink-0 text-vsc-text-dim" /><FolderOpen size={13} className="text-vsc-yellow shrink-0" /></>
            : <><ChevronRight size={12} className="shrink-0 text-vsc-text-dim" /><Folder size={13} className="text-vsc-yellow shrink-0" /></>
          }
          <span className="truncate">{node.name}</span>
          <span className="ml-auto text-[10px] text-vsc-text-dim shrink-0 pr-1">
            {node.children.filter(c => c.type === 'file').length || node.children.length}
          </span>
        </button>
        {open && node.children.map(child => (
          <TreeNode key={child.path} node={child} depth={depth + 1} selected={selected} onSelect={onSelect} />
        ))}
      </div>
    );
  }

  return (
    <button
      onClick={() => onSelect(node)}
      className={`flex items-center gap-1.5 w-full text-left px-2 py-0.5 rounded text-xs transition-colors ${
        isSelected
          ? 'bg-primary/15 text-primary'
          : 'text-vsc-text-dim hover:text-vsc-text hover:bg-white/5'
      }`}
      style={{ paddingLeft: `${8 + depth * 14}px` }}
    >
      {fileIcon(node.name)}
      <span className="truncate">{node.name}</span>
    </button>
  );
};

interface FileTreeViewProps {
  roots: { label: string; node: FileNode; badge?: string }[];
  selected: string | null;
  onSelect: (node: FileNode) => void;
  className?: string;
}

const FileTreeView: React.FC<FileTreeViewProps> = ({ roots, selected, onSelect, className = '' }) => {
  return (
    <div className={`font-mono select-none ${className}`}>
      {roots.map(({ label, node, badge }) => (
        <div key={label} className="mb-3">
          <div className="flex items-center gap-2 px-2 py-1 mb-1">
            <span className="text-[10px] font-bold uppercase tracking-widest text-vsc-text-dim">{label}</span>
            {badge && (
              <span className="text-[9px] px-1.5 py-0.5 bg-primary/10 text-primary rounded font-bold">{badge}</span>
            )}
          </div>
          {node.children.map(child => (
            <TreeNode key={child.path} node={child} depth={0} selected={selected} onSelect={onSelect} />
          ))}
          {node.children.length === 0 && (
            <p className="px-2 text-[11px] text-vsc-text-dim italic">Empty</p>
          )}
        </div>
      ))}
    </div>
  );
};

export default FileTreeView;
