export type UserRole = 'ADMIN' | 'USER';

export const USER_ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Administrateur',
  USER: 'Utilisateur',
};

export interface CurrentUser {
  username: string;
  role: UserRole;
}

export interface AppUser {
  id: number;
  username: string;
  role: UserRole;
  createdAt: string;
}

export interface UserRequest {
  username: string;
  password: string;
  role: UserRole;
}
