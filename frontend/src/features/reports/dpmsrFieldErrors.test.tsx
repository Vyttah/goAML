import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Button, Form, Input } from 'antd';
import type { FormInstance } from 'antd';
import { applyZodIssuesToForm, zodPathToFieldName } from './dpmsrFieldErrors';

describe('zodPathToFieldName', () => {
  it('splits a dot path and turns numeric segments into array indices', () => {
    expect(zodPathToFieldName('parties.0.person.firstName')).toEqual([
      'parties',
      0,
      'person',
      'firstName',
    ]);
    expect(zodPathToFieldName('entityReference')).toEqual(['entityReference']);
    expect(zodPathToFieldName('goods.12.itemType')).toEqual(['goods', 12, 'itemType']);
  });
});

/** A tiny form whose fields mirror the Zod paths we attach errors to. */
function Harness({ onReady }: { onReady: (form: FormInstance) => void }) {
  const [form] = Form.useForm();
  return (
    <Form form={form} layout="vertical">
      <Form.Item label="Entity reference" name="entityReference">
        <Input />
      </Form.Item>
      <Form.List name="parties" initialValue={[{}]}>
        {(fields) =>
          fields.map((f) => (
            <Form.Item key={f.key} label="First name" name={[f.name, 'person', 'firstName']}>
              <Input />
            </Form.Item>
          ))
        }
      </Form.List>
      <Button onClick={() => onReady(form)}>attach</Button>
    </Form>
  );
}

describe('applyZodIssuesToForm', () => {
  it('attaches each Zod issue inline to its form field (scalar + indexed paths)', async () => {
    let form: FormInstance | undefined;
    render(<Harness onReady={(f) => (form = f)} />);
    // grab the form instance
    screen.getByText('attach').click();
    expect(form).toBeDefined();

    applyZodIssuesToForm(form as FormInstance, [
      { path: 'entityReference', message: 'Required' },
      { path: 'parties.0.person.firstName', message: 'Required' },
    ]);

    await waitFor(() => {
      const errs = document.querySelectorAll('.ant-form-item-explain-error');
      expect(errs.length).toBe(2);
    });
    expect(screen.getAllByText('Required').length).toBe(2);
  });

  it('is a no-op for an empty issue list', () => {
    let form: FormInstance | undefined;
    render(<Harness onReady={(f) => (form = f)} />);
    screen.getByText('attach').click();
    applyZodIssuesToForm(form as FormInstance, []);
    expect(document.querySelectorAll('.ant-form-item-explain-error').length).toBe(0);
  });
});
