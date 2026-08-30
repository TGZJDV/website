import { avatarUrl } from '../api';
import type { User } from '../types';

interface AvatarProps {
  user: Pick<User, 'id' | 'username' | 'avatar_key'>;
  size?: 'sm' | 'md' | 'lg';
}

const sizes = {
  sm: 'h-6 w-6 text-xs',
  md: 'h-10 w-10 text-base',
  lg: 'h-16 w-16 text-2xl',
};

/** 用户头像（无头像时显示用户名首字母） */
export default function Avatar({ user, size = 'md' }: AvatarProps) {
  const src = user.avatar_key ? avatarUrl(user) : null;
  return (
    <div
      className={`${sizes[size]} flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary font-bold text-white`}
    >
      {src ? (
        <img src={src} alt={user.username} className="h-full w-full object-cover" />
      ) : (
        user.username.charAt(0).toUpperCase()
      )}
    </div>
  );
}
