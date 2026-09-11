import React from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Login from '../components/Login';
import AuthService from '../services/AuthService';

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));

describe('Login real', () => {
  beforeEach(() => {
    // Use the browser storage supplied by jsdom, not Node's native storage.
    vi.stubGlobal('localStorage', globalThis.jsdom.window.localStorage);
    localStorage.clear();
    vi.useFakeTimers();
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function submit() {
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'cliente@test.local' } });
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'password123' } });
    await act(async () => {
      fireEvent.submit(screen.getByRole('button', { name: 'Ingresar' }).closest('form'));
    });
  }

  it('renderiza los campos del componente', () => {
    render(<Login />);
    expect(screen.getByLabelText('Email').type).toBe('email');
    expect(screen.getByLabelText('Contraseña').type).toBe('password');
  });

  it('envia credenciales y guarda la sesion devuelta por el backend', async () => {
    const user = { id: 1, email: 'cliente@test.local', role: 'CLIENTE' };
    const login = vi.spyOn(AuthService, 'login').mockResolvedValue({ data: { token: 'test-token', user } });
    render(<Login />);
    await submit();
    expect(login).toHaveBeenCalledWith({ email: 'cliente@test.local', password: 'password123' });
    expect(localStorage.getItem('jwtToken')).toBe('test-token');
    expect(AuthService.getCurrentUser()).toEqual(user);
    expect(screen.getByText(/Login exitoso/)).toBeTruthy();
  });

  it('muestra el rechazo del backend sin crear una sesion', async () => {
    vi.spyOn(AuthService, 'login').mockRejectedValue({ response: { status: 401, data: { error: 'Credenciales incorrectas' } } });
    render(<Login />);
    await submit();
    expect(screen.getByText('Error: Credenciales incorrectas')).toBeTruthy();
    expect(localStorage.getItem('jwtToken')).toBeNull();
  });

  it('informa cuando no puede conectar con el servidor', async () => {
    vi.spyOn(AuthService, 'login').mockRejectedValue(new Error('Network Error'));
    render(<Login />);
    await submit();
    expect(screen.getByText('Error: No se pudo conectar con el servidor.')).toBeTruthy();
    expect(localStorage.getItem('jwtToken')).toBeNull();
  });
});
