"use client";

interface NewThreadButtonProps {
  onClick: () => void;
}

export function NewThreadButton({ onClick }: NewThreadButtonProps) {
  return (
    <button
      onClick={onClick}
      className="w-full px-3 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-md hover:bg-gray-50 transition-colors flex items-center gap-2"
    >
      <span className="text-lg leading-none">+</span>
      <span>New chat</span>
    </button>
  );
}
