export type Perfil = 'USER' | 'LIBRARIAN' | 'ADMIN';

export const PERFIL_LABELS: Record<Perfil, string> = {
  USER: 'Leitor',
  LIBRARIAN: 'Bibliotecário',
  ADMIN: 'Administrador',
};

export interface Credentials {
  email: string;
  password: string;
}

export interface CreateUser extends Credentials {
  nome: string;
}

export interface AuthResponse {
  token: string;
}

export interface Session {
  token: string;
  email: string;
  nome: string | null;
  perfis: Perfil[];
  expiresAt: number;
}
