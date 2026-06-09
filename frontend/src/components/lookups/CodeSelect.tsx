import { Select } from 'antd';
import { useLookupEntries } from '../../features/lookups/useLookups';

interface CodeSelectProps {
  /** Lookup set name (e.g. "countries", "currencies", "item_types"). */
  set: string;
  /** Single-select value, or an array when `multiple`. */
  value?: string | string[];
  onChange?: (value: string | string[] | undefined) => void;
  placeholder?: string;
  allowClear?: boolean;
  disabled?: boolean;
  /** Render as a multi-select (e.g. report indicators). */
  multiple?: boolean;
  /** Injected by AntD Form.Item (label association). Forwarded to the underlying Select. */
  id?: string;
}

/**
 * Searchable dropdown backed by a backend lookup set, so the choices always match server validation.
 * Designed as a Form.Item child (AntD injects value/onChange). Shows "CODE — label" when the backend
 * supplies a distinct label (the submitted value is always the bare code); filtering matches code or label.
 */
export function CodeSelect({
  set,
  value,
  onChange,
  placeholder,
  allowClear = true,
  disabled,
  multiple = false,
  id,
}: CodeSelectProps) {
  const { data, isLoading } = useLookupEntries(set);
  const options = (data ?? []).map(({ code, label }) => ({
    value: code,
    label: label && label !== code ? `${code} — ${label}` : code,
  }));
  return (
    <Select
      id={id}
      mode={multiple ? 'multiple' : undefined}
      showSearch
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      allowClear={allowClear}
      disabled={disabled}
      loading={isLoading}
      options={options}
      optionFilterProp="label"
      maxTagCount={multiple ? 'responsive' : undefined}
      style={{ width: '100%' }}
    />
  );
}
