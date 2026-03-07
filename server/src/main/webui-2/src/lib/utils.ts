import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatLastUpdate(date: Date) {
  const time = new Date().getTime() - date.getTime()
  const seconds = Math.floor(time / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);


  if (days > 1) return `${days} days ago`
  if (days > 0) return `One day ago`

  if (hours > 1) return `${hours} hours ago`
  if (hours > 0) return `One hour ago`

  if (minutes > 1) return `${minutes} minutes ago`
  if (minutes > 0) return `One minute ago`

  if (seconds > 1) `${seconds} seconds ago`
  if (seconds > 0) return `One second ago`

  return null
}